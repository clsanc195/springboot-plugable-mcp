package com.mcp.mcphostapp.service;

import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Service
public class TimeService {

    public String getCurrentTime(String timezone) {
        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }

    public Set<String> getAvailableTimezones() {
        return ZoneId.getAvailableZoneIds();
    }
}
