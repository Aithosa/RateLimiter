package com.demo.ratelimiter.common;

/**
 * 开关枚举
 *
 * @author w00585603
 */
public enum Constant {
    /**
     * 开启状态
     */
    ON("1"),

    /**
     * 关闭状态
     */
    OFF("0");

    Constant(String code) {
        this.code = code;
    }

    Constant(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private String code;

    private String desc;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public static String getDesc(String code) {
        Constant[] enums = Constant.values();
        for (Constant constant : enums) {
            if (constant.getCode().equals(code)) {
                return constant.getDesc();
            }
        }
        return "";
    }
}
