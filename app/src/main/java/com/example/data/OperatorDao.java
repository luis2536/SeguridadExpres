package com.example.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface OperatorDao {
    @Query("SELECT * FROM operators ORDER BY registrationDate DESC")
    List<Operator> getAllOperators();

    @Insert
    void insert(Operator operator);

    @Query("DELETE FROM operators")
    void deleteAll();
}
