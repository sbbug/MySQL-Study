package com.sun.test;


import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Main {
    private static Connection connection = null;

        static {
                try {
                         Class.forName("ru.yandex.clickhouse.ClickHouseDriver");
                         String url = "jdbc:clickhouse://localhost:8080/system";// url路径
                         String user = "default";// 账号
                         String password = "";// 密码
                         connection = DriverManager.getConnection(url, user, password);
                } catch (Exception e) {
                         e.printStackTrace();
                     }
        }
    public static void main(String[] args){

        Statement statement = null;
        try {
            statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("select 100");
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            while (resultSet.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    System.out.println(metaData.getColumnName(i) + ":" + resultSet.getString(i));
                }
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

    }
}
