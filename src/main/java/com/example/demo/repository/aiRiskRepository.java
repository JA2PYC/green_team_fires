package com.example.demo.repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

public class aiRiskRepository {
        private final JdbcTemplate jdbcTemplate;

    public aiRiskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> getRiskData() {
        String sql = "SELECT Region, Cause, `Reduction (%)` FROM dy_risk_analysis ORDER BY `Reduction (%)` DESC";
        return jdbcTemplate.queryForList(sql);
    }
}
