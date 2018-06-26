package myproj;

import java.io.Serializable;
import java.util.ArrayList;

public class PathResult implements Serializable {
    private double totalPrice;
    private ArrayList<Integer> path;

    public PathResult(double totalPrice, ArrayList<Integer> path) {
        this.totalPrice = totalPrice;
        this.path =  new ArrayList<Integer>(path);
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public void setPath(ArrayList<Integer> path) {
        this.path = path;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public ArrayList<Integer> getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "PathResult{" +
                "totalPrice=" + totalPrice +
                ", path=" + path +
                '}';
    }
}