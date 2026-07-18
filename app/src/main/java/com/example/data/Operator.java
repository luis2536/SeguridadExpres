package com.example.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "operators")
public class Operator {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String username;
    public String role;
    public long registrationDate;

    public Operator() {
    }

    public Operator(String username, String role, long registrationDate) {
        this.username = username;
        this.role = role;
        this.registrationDate = registrationDate;
    }
}
