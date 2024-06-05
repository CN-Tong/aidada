package com.tong.aidada.scoring;

import com.tong.aidada.model.entity.App;
import com.tong.aidada.model.entity.UserAnswer;

import java.util.List;

/**
 * 评分策略接口
 */
public interface ScoringStrategy {

    /**
     * 执行评分
     *
     * @param choices 用户答案列表
     * @param app 应用
     * @return 用户答题记录
     * @throws Exception
     */
    UserAnswer doScore(List<String> choices, App app) throws Exception;
}
