package org.example.competition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.beans.factory.parsing.Problem;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("competitions")
public class Competition {
    @TableId(type = IdType.AUTO)
    private Integer competitionId;

    private String competitionName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private LocalDateTime createTime;

    // --- 非数据库映射字段 ---

    /**
     * 该比赛关联的题目列表
     */
    @TableField(exist = false)
    private List<Object> problems;

    /**
     * 当前登录用户是否已报名
     */
    @TableField(exist = false)
    private Boolean isRegistered;
}