package myproj;

import myproj.map.AdjMatrixEdgeWeightedDigraph;
import myproj.map.DirectedEdge;
import myproj.map.FloydWarshall;

import java.util.ArrayList;

public class CityMap {
    private static AdjMatrixEdgeWeightedDigraph cityMap;
    private static int storageVertex;
    private static FloydWarshall fwMatrix;

    public static void initMap(AdjMatrixEdgeWeightedDigraph digraph) {
        cityMap = digraph;
    }

    /**
     * Set the location of storage
     * @param v storage vertex
     * @return
     */
    public static boolean setStorageVertex(int v)  {
        storageVertex = v;
        return storageVertex > 0 && storageVertex < cityMap.V();
    }

    public static int getStorageVertex() {
        return storageVertex;
    }

    public static String getCityMapString() {
        return cityMap.toString();
    }

    public static void createFWMatrix(){
        fwMatrix = new FloydWarshall(cityMap);
    }

    public static boolean hasPath(int o, int d){
        return fwMatrix.hasPath(o,d);
    }

    public static double getShortestDist(int o, int d){
        return fwMatrix.dist(o,d);
    }

    public static Iterable<DirectedEdge> getShortestPath(int o, int d){
        return fwMatrix.path(o,d);
    }

    // find shortest path from origin to destination through newPoint and path point
    public static PathResult findShortPath(int o, int d, int newPoint, ArrayList<Integer> path) {
        double result = fwMatrix.dist(o,storageVertex);
        int pathSize = path.size();
        double diffPrice = fwMatrix.dist(path.get(0), newPoint) + fwMatrix.dist(newPoint, path.get(1)) - fwMatrix.dist(path.get(0), path.get(1));
        int index = 1;
        // find optimal order
        for(int i=1; i < pathSize-1; i++){
            double newDiff = fwMatrix.dist(path.get(i), newPoint) + fwMatrix.dist(newPoint, path.get(i+1)) - fwMatrix.dist(path.get(i), path.get(i+1));
            if (newDiff < diffPrice) {
                index = i+1;
                diffPrice = newDiff;
            }
        }
        if(!(path.get(index) == newPoint || path.get(index-1)== newPoint)){
            path.add(index, newPoint);
        }
        pathSize = path.size();
        for(int i=0; i<pathSize-1; i++) {
            result = result + fwMatrix.dist(path.get(i), path.get(i+1));
        }
        PathResult pathResult = new PathResult(result, path);
        return pathResult;
    }
}
