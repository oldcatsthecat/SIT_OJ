package org.example.problem.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.example.common.entity.BaseEntity; // 引入 common 中的基类
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true) // 必须加上，确保 equals 和 hashCode 包含父类字段
@TableName(value = "problems", autoResultMap = true)
public class Problem extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Integer problemId;
    private String problemName;
    private String problemDescription;
    private String inputDescription;
    private String outputDescription;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Sample> samples;

    @TableField("is_public")
    private Boolean isPublic;

    private String hint;
    private Integer timeLimit;
    private Integer memoryLimit;
    private String difficulty;
    private String problemSource;
    private Integer acceptedNumber;
    private Integer submissionNumber;

    private Integer judgeType;
    private String spjCode;

    @TableField(exist = false)
    private Boolean isSolved = false; // 非数据库字段，默认为 false

}