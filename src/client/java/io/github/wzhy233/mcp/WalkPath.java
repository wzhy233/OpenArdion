package io.github.wzhy233.mcp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;

final class WalkPath {
    record Point(int x, int y, int z) {
        Point offset(int x, int y, int z) { return new Point(this.x + x, this.y + y, this.z + z); }
        double distance(Point other) {
            return Math.sqrt(Math.pow(x - (double) other.x, 2) + Math.pow(y - (double) other.y, 2) + Math.pow(z - (double) other.z, 2));
        }
    }
    interface Grid {
        boolean clear(Point point);
        boolean standable(Point point);
    }
    private record Node(Point point, double cost, double estimate) { }

    static List<Point> find(Point start, Point goal, double range, Grid grid) {
        var queue = new PriorityQueue<Node>(Comparator.comparingDouble(Node::estimate));
        var cost = new HashMap<Point, Double>();
        var previous = new HashMap<Point, Point>();
        cost.put(start, 0.0);
        queue.add(new Node(start, 0, Math.max(0, start.distance(goal) - range)));
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 4096) {
            Node node = queue.remove();
            if (node.cost() > cost.getOrDefault(node.point(), Double.MAX_VALUE)) continue;
            if (node.point().distance(goal) <= range) {
                var route = new ArrayList<Point>();
                Point point = node.point();
                while (!point.equals(start)) {
                    route.addFirst(point);
                    point = previous.get(point);
                }
                return route;
            }
            for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                Point side = node.point().offset(direction[0], 0, direction[1]);
                for (int dy : new int[]{0, 1, -1, -2, -3}) {
                    Point next = side.offset(0, dy, 0);
                    if (start.distance(next) > 64 || !grid.standable(next)) continue;
                    if (dy > 0 && !grid.clear(node.point().offset(0, 1, 0))) continue;
                    boolean blocked = false;
                    for (int y = 0; y > dy; y--) if (!grid.clear(side.offset(0, y, 0))) blocked = true;
                    if (blocked) continue;
                    double distance = node.cost() + 1 + Math.abs(dy) * 0.5;
                    if (distance >= cost.getOrDefault(next, Double.MAX_VALUE)) continue;
                    cost.put(next, distance);
                    previous.put(next, node.point());
                    queue.add(new Node(next, distance, distance + Math.max(0, next.distance(goal) - range)));
                }
            }
        }
        throw new IllegalStateException("No safe loaded walking route found within the local search budget. Try a nearer waypoint or another route; no movement was claimed.");
    }
}
