package com.fasterxml.jackson.module.blackbird;

import java.util.Random;

// Mostly primitive doubles, with values as short as most real ones
public class DoubleBean {
    private int id;
    private double latitude;
    private double longitude;
    private double altitude;
    private double speed;
    private double heading;
    private double accuracy;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getAltitude() { return altitude; }
    public void setAltitude(double altitude) { this.altitude = altitude; }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }

    public double getHeading() { return heading; }
    public void setHeading(double heading) { this.heading = heading; }

    public double getAccuracy() { return accuracy; }
    public void setAccuracy(double accuracy) { this.accuracy = accuracy; }

    static DoubleBean random(Random random) {
        final DoubleBean result = new DoubleBean();
        result.setId(random.nextInt());
        result.setLatitude(round(random.nextDouble() * 180.0 - 90.0, 1e6));
        result.setLongitude(round(random.nextDouble() * 360.0 - 180.0, 1e6));
        result.setAltitude(round(random.nextDouble() * 9000.0, 1e2));
        result.setSpeed(round(random.nextDouble() * 300.0, 1e2));
        result.setHeading(round(random.nextDouble() * 360.0, 1e2));
        result.setAccuracy(round(random.nextDouble() * 50.0, 1e2));
        return result;
    }

    private static double round(double value, double scale) {
        return Math.round(value * scale) / scale;
    }
}
