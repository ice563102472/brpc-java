package com.baidu.brpc.utils;


import com.google.gson.reflect.TypeToken;
import org.junit.Test;

import java.util.Date;

public class GsonUtilsTest {

    @Test
    public void fromJson() {
        Date date = new Date();
        Date d1 = GsonUtils.fromJson(GsonUtils.toJson(date), new TypeToken<Date>(){}.getType());
        Date d2 = GsonUtils.fromJson(GsonUtils.toJson(date), new TypeToken<java.sql.Date>(){}.getType());

        date = new java.sql.Date(System.currentTimeMillis());
        d1 = GsonUtils.fromJson(GsonUtils.toJson(date), new TypeToken<Date>(){}.getType());
        d2 = GsonUtils.fromJson(GsonUtils.toJson(date), new TypeToken<java.sql.Date>(){}.getType());

        java.sql.Date sDate = new java.sql.Date(System.currentTimeMillis());
        java.sql.Date d3 = GsonUtils.fromJson(GsonUtils.toJson(date), new TypeToken<java.sql.Date>(){}.getType());


    }
}