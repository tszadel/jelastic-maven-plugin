package com.jelastic.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * User: Igor.Yova@gmail.com
 * Date: 6/9/11
 * Time: 12:24 PM
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Debug {
    private String time;
    private Cpu cpu;

    public static class Cpu {
        private String usage, time;

        public void setUsage(String usage) {
            this.usage = usage;
        }

        public void setTime(String time) {
            this.time = time;
        }

        public String getUsage() {
            return usage;
        }

        public String getTime() {
            return time;
        }
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public Cpu getCpu() {
        return cpu;
    }

    public void setCpu(Cpu cpu) {
        this.cpu = cpu;
    }
}
