package org.luaj.vm2.lib.jse;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaUserdata;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.CoroutineLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.luajc.JavaGen;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LuaJavaBridge extends LuajavaLib {
    private static final Map<Class<?>, Members> CACHE = new ConcurrentHashMap<>();
    private static volatile boolean checked;
    private static volatile String namespace;
    private static volatile Object mappings;
    private static volatile int fromId = -2;
    private static volatile int toId = -2;

    public static void install(Globals globals) {
        if (!checked) {
            selfCheck();
            checked = true;
        }
        globals.load(new JseBaseLib());
        globals.load(new PackageLib());
        globals.load(new Bit32Lib());
        globals.load(new TableLib());
        globals.load(new StringLib());
        globals.load(new CoroutineLib());
        globals.load(new JseMathLib());
        globals.load(new JseIoLib());
        globals.load(new JseOsLib());
        globals.load(new LuaJavaBridge());
        LoadState.install(globals);
        LuaC.install(globals);
        globals.loader = LuaJavaBridge::compile;
    }

    public static LuaValue toLua(Object value) {
        return reopen(CoerceJavaToLua.coerce(value));
    }

    @Override
    public Varargs invoke(Varargs args) {
        try {
            if (opcode == BINDCLASS) return OpenJava.wrap(classForName(args.checkjstring(1)));
            if (opcode == NEW || opcode == NEWINSTANCE) {
                Class<?> type = opcode == NEWINSTANCE ? classForName(args.checkjstring(1)) : (Class<?>) args.arg1().checkuserdata(Class.class);
                return OpenJava.construct(type, args.subargs(2));
            }
            return super.invoke(args);
        } catch (LuaError error) {
            throw error;
        } catch (Exception error) {
            throw new LuaError(error);
        }
    }

    @Override
    protected Class<?> classForName(String name) throws ClassNotFoundException {
        ClassLoader loader = LuaJavaBridge.class.getClassLoader();
        if (loader == null) loader = ClassLoader.getSystemClassLoader();
        Set<String> names = new LinkedHashSet<>();
        names.add(name);
        try {
            MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();
            if (resolver.getNamespaces().contains("named")) names.add(resolver.mapClassName("named", name));
            if (resolver.getNamespaces().contains("intermediary")) names.add(resolver.mapClassName("intermediary", name));
        } catch (Throwable ignored) {
        }
        ClassNotFoundException last = null;
        for (String candidate : names) {
            try {
                return Class.forName(candidate, true, loader);
            } catch (ClassNotFoundException error) {
                last = error;
            }
        }
        throw last == null ? new ClassNotFoundException(name) : last;
    }

    private static LuaValue reopen(Varargs value) {
        if (!(value instanceof LuaUserdata data) || value instanceof OpenJava) return value instanceof LuaValue lua ? lua : LuaValue.NIL;
        Object object = data.touserdata();
        if (object == null || object.getClass().isArray()) return (LuaValue) value;
        return OpenJava.wrap(object);
    }

    private static void selfCheck() {
        Globals globals = new Globals();
        globals.load(new org.luaj.vm2.lib.BaseLib());
        globals.load(new PackageLib());
        globals.load(new LuaJavaBridge());
        LoadState.install(globals);
        LuaC.install(globals);
        try {
            globals.load("local c = luajava.bindClass('org.luaj.vm2.lib.jse.LuaJavaBridge$Probe')\n"
                    + "local p = c:new()\n"
                    + "if p.hidden ~= 7 then error('hidden') end\n"
                    + "p.hidden = 4\n"
                    + "if p:secret() ~= 4 then error('secret') end\n", "lua-access-check").call();
        } catch (LuaError error) {
            throw new IllegalStateException("lua full access check failed: " + error.getMessage(), error);
        }
        Globals compiled = new Globals();
        compiled.load(new org.luaj.vm2.lib.BaseLib());
        LoadState.install(compiled);
        LuaC.install(compiled);
        compiled.loader = LuaJavaBridge::compile;
        try {
            LuaValue chunk = compiled.load("return 1", "lua-bytecode-check");
            if (chunk.isclosure() || chunk.call().toint() != 1) throw new IllegalStateException("lua stayed interpreted");
        } catch (IllegalStateException error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("lua bytecode check failed: " + error.getMessage(), error);
        }
    }

    private static LuaFunction compile(Prototype prototype, String name, LuaValue env) throws IOException {
        String filename = luaFilename(name);
        String classname = javaClassname(filename);
        JavaGen gen = new JavaGen(prototype, classname, filename, false);
        ClassLoader parent = LuaJavaBridge.class.getClassLoader();
        ChunkLoader loader = new ChunkLoader(parent == null ? ClassLoader.getSystemClassLoader() : parent);
        define(loader, gen);
        try {
            Class<?> type = loader.loadClass(classname);
            LuaFunction function = (LuaFunction) type.getDeclaredConstructor().newInstance();
            try {
                type.getMethod("initupvalue1", LuaValue.class).invoke(function, env);
            } catch (NoSuchMethodException ignored) {
            }
            return function;
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IOException(name, error);
        }
    }

    private static void define(ChunkLoader loader, JavaGen gen) {
        if (gen.inners != null) for (JavaGen inner : gen.inners) define(loader, inner);
        loader.define(gen.classname, gen.bytecode);
    }

    private static String luaFilename(String chunk) {
        String stub = chunk.endsWith(".lua") ? chunk.substring(0, chunk.length() - 4) : chunk;
        String filename = stub.replace('.', '/') + ".lua";
        return filename.startsWith("@") ? filename.substring(1) : filename;
    }

    private static String javaClassname(String chunk) {
        String stub = chunk.endsWith(".lua") ? chunk.substring(0, chunk.length() - 4) : chunk;
        StringBuilder name = new StringBuilder(stub.length());
        for (int i = 0; i < stub.length(); i++) {
            char c = stub.charAt(i);
            name.append((i == 0 ? Character.isJavaIdentifierStart(c) : Character.isJavaIdentifierPart(c)) ? c : '_');
        }
        return name.toString();
    }

    private static final class ChunkLoader extends ClassLoader {
        ChunkLoader(ClassLoader parent) { super(parent); }
        void define(String name, byte[] bytes) { defineClass(name, bytes, 0, bytes.length); }
    }

    public static final class Probe {
        private int hidden = 7;

        private int secret() {
            return hidden;
        }
    }

    private static final class OpenJava extends JavaInstance {
        OpenJava(Object instance) {
            super(instance);
        }

        static LuaValue wrap(Object instance) {
            return new OpenJava(instance);
        }

        static Varargs construct(Class<?> type, Varargs args) {
            LuaValue ctor = members(type).method("new");
            if (ctor == null || ctor.isnil()) throw new LuaError("no constructor");
            return ctor.invoke(args);
        }

        @Override
        public LuaValue get(LuaValue key) {
            Members members = members(javaType());
            Field field = members.field(key.tojstring());
            if (field != null) {
                try {
                    return reopen(CoerceJavaToLua.coerce(field.get(m_instance)));
                } catch (Exception error) {
                    throw new LuaError(error);
                }
            }
            LuaValue method = members.method(key.tojstring());
            if (method != null) return method;
            Class<?> inner = members.inner(key.tojstring());
            if (inner != null) return wrap(inner);
            return NIL;
        }

        @Override
        public void set(LuaValue key, LuaValue value) {
            Field field = members(javaType()).field(key.tojstring());
            if (field == null) throw new LuaError("no field " + key.tojstring());
            try {
                field.set(m_instance, CoerceLuaToJava.coerce(value, field.getType()));
            } catch (RuntimeException error) {
                throw new LuaError(error);
            } catch (Exception error) {
                throw new LuaError(error);
            }
        }

        private Class<?> javaType() {
            return m_instance instanceof Class<?> type ? type : m_instance.getClass();
        }
    }

    private static Members members(Class<?> type) {
        return CACHE.computeIfAbsent(type, Members::build);
    }

    private static final class Members {
        final Map<String, LuaValue> methods = new LinkedHashMap<>();
        final Map<String, Field> fields = new LinkedHashMap<>();
        final Map<String, Class<?>> inners = new LinkedHashMap<>();

        static Members build(Class<?> type) {
            Members members = new Members();
            Map<String, List<JavaMethod>> found = new LinkedHashMap<>();
            for (Method method : methods(type)) {
                if (method.isSynthetic() || method.isBridge()) continue;
                try {
                    method.setAccessible(true);
                } catch (Throwable ignored) {
                    continue;
                }
                add(found, method.getName(), method);
                String friendly = friendly(method, descriptor(method), true);
                if (!friendly.equals(method.getName())) add(found, friendly, method);
            }
            found.forEach((name, list) -> {
                if ("new".equals(name)) return;
                LuaValue value = list.size() == 1 ? list.get(0) : JavaMethod.forMethods(list.toArray(JavaMethod[]::new));
                members.methods.put(name, wrap(value));
            });
            LuaValue ctor = constructors(type);
            if (ctor != null) members.methods.put("new", ctor);
            for (Field field : fields(type)) {
                if (field.isSynthetic()) continue;
                try {
                    field.setAccessible(true);
                } catch (Throwable ignored) {
                    continue;
                }
                members.fields.putIfAbsent(field.getName(), field);
                members.fields.putIfAbsent(friendly(field, descriptor(field.getType()), false), field);
            }
            for (Class<?> inner : type.getDeclaredClasses()) members.inners.putIfAbsent(inner.getSimpleName(), inner);
            return members;
        }

        Field field(String name) {
            return fields.get(name);
        }

        LuaValue method(String name) {
            return methods.get(name);
        }

        Class<?> inner(String name) {
            return inners.get(name);
        }

        private static void add(Map<String, List<JavaMethod>> found, String name, Method method) {
            found.computeIfAbsent(name, key -> new ArrayList<>()).add(JavaMethod.forMethod(method));
        }

        private static LuaValue wrap(LuaValue method) {
            return new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    return reopen(method.invoke(args));
                }
            };
        }

        private static LuaValue constructors(Class<?> type) {
            List<JavaConstructor> list = new ArrayList<>();
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                try {
                    constructor.setAccessible(true);
                } catch (Throwable ignored) {
                    continue;
                }
                list.add(JavaConstructor.forConstructor(constructor));
            }
            if (list.isEmpty()) return null;
            LuaValue raw = list.size() == 1 ? list.get(0) : JavaConstructor.forConstructors(list.toArray(JavaConstructor[]::new));
            return new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    if (type.equals(args.arg1().touserdata())) args = args.subargs(2);
                    return reopen(raw.invoke(args));
                }
            };
        }
    }

    private static List<Method> methods(Class<?> type) {
        List<Method> list = new ArrayList<>();
        walk(type, new HashSet<>(), list, null);
        return list;
    }

    private static List<Field> fields(Class<?> type) {
        List<Field> list = new ArrayList<>();
        walk(type, new HashSet<>(), null, list);
        return list;
    }

    private static void walk(Class<?> type, Set<Class<?>> seen, List<Method> methods, List<Field> fields) {
        if (type == null || !seen.add(type)) return;
        if (methods != null) java.util.Collections.addAll(methods, type.getDeclaredMethods());
        if (fields != null) java.util.Collections.addAll(fields, type.getDeclaredFields());
        walk(type.getSuperclass(), seen, methods, fields);
        for (Class<?> face : type.getInterfaces()) walk(face, seen, methods, fields);
    }

    private static String friendly(Member member, String descriptor, boolean method) {
        String runtime = namespace();
        if (runtime == null || "named".equals(runtime)) return member.getName();
        try {
            Object tree = mappings();
            int from = fromId;
            int to = toId;
            if (tree == null || from < 0 || to < 0) return member.getName();
            String owner = member.getDeclaringClass().getName().replace('.', '/');
            Object mapping = tree.getClass().getMethod(method ? "getMethod" : "getField", String.class, String.class, String.class, int.class)
                    .invoke(tree, owner, member.getName(), descriptor, from);
            if (mapping == null) return member.getName();
            String name = (String) mapping.getClass().getMethod("getName", int.class).invoke(mapping, to);
            return name == null || name.isEmpty() ? member.getName() : name;
        } catch (Throwable ignored) {
            return member.getName();
        }
    }

    private static String namespace() {
        String current = namespace;
        if (current != null) return current;
        try {
            current = FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace();
        } catch (Throwable ignored) {
            current = "named";
        }
        namespace = current;
        return current;
    }

    private static Object mappings() {
        Object tree = mappings;
        if (tree != null || fromId == -1) return tree;
        try {
            Object current = FabricLoader.getInstance().getMappingResolver();
            for (int depth = 0; depth < 4 && current != null; depth++) {
                Object next = null;
                for (Field field : current.getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    Object value = field.get(current);
                    if (value == null) continue;
                    if (field.getType().getName().contains("MappingTree")) {
                        tree = value;
                        break;
                    }
                    if (next == null && value instanceof MappingResolver && value != current) next = value;
                }
                if (tree != null) break;
                current = next;
            }
            if (tree != null) {
                fromId = (int) tree.getClass().getMethod("getNamespaceId", String.class).invoke(tree, namespace());
                toId = (int) tree.getClass().getMethod("getNamespaceId", String.class).invoke(tree, "named");
            }
        } catch (Throwable ignored) {
            tree = null;
        }
        if (tree == null) fromId = -1;
        mappings = tree;
        return tree;
    }

    private static String descriptor(Method method) {
        StringBuilder desc = new StringBuilder("(");
        for (Class<?> parameter : method.getParameterTypes()) desc.append(descriptor(parameter));
        return desc.append(')').append(descriptor(method.getReturnType())).toString();
    }

    private static String descriptor(Class<?> type) {
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        if (type == double.class) return "D";
        if (type == void.class) return "V";
        if (type.isArray()) return type.getName().replace('.', '/');
        return "L" + type.getName().replace('.', '/') + ";";
    }
}
