package com.example.carbonwise;

/**
 * One generated report. Stored in Firebase Realtime Database at users/{uid}/saved_reports/{id}.
 * The Realtime Database mapper needs the public no-argument constructor and a getter/setter for
 * every property.
 */
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

    public SavedReport() {} // Required by the database mapper

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
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPeriodType() { return periodType; }
    public void setPeriodType(String periodType) { this.periodType = periodType; }

    public String getDateGenerated() { return dateGenerated; }
    public void setDateGenerated(String dateGenerated) { this.dateGenerated = dateGenerated; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public double getTotalCo2() { return totalCo2; }
    public void setTotalCo2(double totalCo2) { this.totalCo2 = totalCo2; }

    public double getDailyAvg() { return dailyAvg; }
    public void setDailyAvg(double dailyAvg) { this.dailyAvg = dailyAvg; }

    public int getDaysOverBudget() { return daysOverBudget; }
    public void setDaysOverBudget(int daysOverBudget) { this.daysOverBudget = daysOverBudget; }

    public double getElectricityCo2() { return electricityCo2; }
    public void setElectricityCo2(double electricityCo2) { this.electricityCo2 = electricityCo2; }

    public double getTransportCo2() { return transportCo2; }
    public void setTransportCo2(double transportCo2) { this.transportCo2 = transportCo2; }

    public double getMealsCo2() { return mealsCo2; }
    public void setMealsCo2(double mealsCo2) { this.mealsCo2 = mealsCo2; }

    public double getCookingCo2() { return cookingCo2; }
    public void setCookingCo2(double cookingCo2) { this.cookingCo2 = cookingCo2; }
}
