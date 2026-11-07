package com.example.carbonwise;

public class SavedReport {
    private String id;
    private String title;
    private String periodType; // "7_DAYS" or "30_DAYS"
    private String dateGenerated;
    private long timestamp;
    private double totalCo2;
    private double dailyAvg;
    private int daysOverBudget;
    private double electricityCo2;
    private double transportCo2;
    private double mealsCo2;
    private double cookingCo2;

    public SavedReport() {} // Required for Firestore

    public SavedReport(String id, String title, String periodType, String dateGenerated,
                       long timestamp, double totalCo2, double dailyAvg, int daysOverBudget,
                       double electricityCo2, double transportCo2, double mealsCo2, double cookingCo2) {
        this.id = id;
        this.title = title;
        this.periodType = periodType;
        this.dateGenerated = dateGenerated;
        this.timestamp = timestamp;
        this.totalCo2 = totalCo2;
        this.dailyAvg = dailyAvg;
        this.daysOverBudget = daysOverBudget;
        this.electricityCo2 = electricityCo2;
        this.transportCo2 = transportCo2;
        this.mealsCo2 = mealsCo2;
        this.cookingCo2 = cookingCo2;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getPeriodType() { return periodType; }
    public String getDateGenerated() { return dateGenerated; }
    public long getTimestamp() { return timestamp; }
    public double getTotalCo2() { return totalCo2; }
    public double getDailyAvg() { return dailyAvg; }
    public int getDaysOverBudget() { return daysOverBudget; }
    public double getElectricityCo2() { return electricityCo2; }
    public double getTransportCo2() { return transportCo2; }
    public double getMealsCo2() { return mealsCo2; }
    public double getCookingCo2() { return cookingCo2; }
}