package myproj;

import java.io.Serializable;
import java.util.ArrayList;

public class Offer implements Serializable {
    private int price;
    private int location;

    public Offer(int price, int location){
        this.price = price;
        this.location = location;
    }

    public int getPrice(){
        return price;
    }

    public int getLocation(){
        return location;
    }


    @Override
    public String toString() {
        return "Offer{" +
                "price=" + price +
                ", location=" + location +
                '}';
    }
}
