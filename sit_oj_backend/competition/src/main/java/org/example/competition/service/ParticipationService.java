package org.example.competition.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.competition.entity.Participation;
import org.example.common.utils.Result; // 确保路径正确

public interface ParticipationService extends IService<Participation> {
    // 修改返回类型为 Result
    Result<String> registerUser(Integer cid, Integer userId);
    boolean checkRegistration(Integer cid, Integer userId);

}