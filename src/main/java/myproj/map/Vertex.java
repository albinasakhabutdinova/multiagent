package myproj.map;

import java.util.ArrayList;

public class Vertex {

    private String label;
    private ArrayList<Edge> neighbors;


    public Vertex(String label){
        this.label = label;
        this.neighbors = new ArrayList<Edge>();
    }

    public void addNeighbor(Edge edge){
        if(this.neighbors.contains(edge)){
            return;
        }
        this.neighbors.add(edge);
    }

    public boolean containsNeighbor(Edge otherEdge){
        return this.neighbors.contains(otherEdge);
    }

    public Edge getNeighbor(int index){
        return this.neighbors.get(index);
    }


    Edge removeNeighbor(int index){
        return this.neighbors.remove(index);
    }

    public void removeNeighbor(Edge edge){
        this.neighbors.remove(edge);
    }

    public int getNeighborCount(){
        return this.neighbors.size();
    }

    public String getLabel(){
        return this.label;
    }

    public String toString(){
        return "Vertex " + this.label;
    }

    public int hashCode(){
        return this.label.hashCode();
    }

    public boolean equals(Object other){
        if(!(other instanceof Vertex)){
            return false;
        }
        Vertex v = (Vertex)other;
        return this.label.equals(v.label);
    }

    public ArrayList<Edge> getNeighbors(){
        return new ArrayList<Edge>(this.neighbors);
    }

}
