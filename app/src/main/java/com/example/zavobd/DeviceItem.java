package com.example.zavobd;

// This is a simple data holder class (also called a POJO)
public class DeviceItem {
    private String deviceName;
    private String deviceAddress;

    public DeviceItem(String name, String address) {
        this.deviceName = name;
        this.deviceAddress = address;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }

    // This is the important part! The ListView will call this method
    // to get the text to display for each item. We just return the name.
    @Override
    public String toString() {
        return deviceName;
    }
}