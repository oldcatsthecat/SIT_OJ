package org.example.submission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("submissions")
public class Submission {
    @TableId(type = IdType.AUTO)
    private Integer submissionId;
    private Integer userId;
    private Integer problemId;
    private Integer competitionId;
    private String codeContent;
    private String language;
    private String status;
    private Integer timeCost;
    private Integer memoryCost;
    private String judgeInfo;
    private String errorMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submissionTime;

    @TableField(exist = false)
    private String username;

    @TableField(exist = false)
    private String problemName;

    @TableField(exist = false)
    private Boolean canSeeDetail; // 新增字段：控制详情查看权限

}