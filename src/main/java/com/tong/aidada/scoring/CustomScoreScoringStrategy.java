package com.tong.aidada.scoring;

import cn.hutool.json.JSONUtil;
import com.tong.aidada.model.dto.question.QuestionContentDTO;
import com.tong.aidada.model.entity.App;
import com.tong.aidada.model.entity.Question;
import com.tong.aidada.model.entity.ScoringResult;
import com.tong.aidada.model.entity.UserAnswer;
import com.tong.aidada.model.vo.QuestionVO;
import com.tong.aidada.service.QuestionService;
import com.tong.aidada.service.ScoringResultService;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * 自定义得分类应用评分策略
 */
@ScoringStrategyConfig(appType = 0, scoringStrategy = 0)
public class CustomScoreScoringStrategy implements ScoringStrategy{

    @Resource
    private QuestionService questionService;

    @Resource
    private ScoringResultService scoringResultService;

    @Override
    public UserAnswer doScore(List<String> choices, App app) throws Exception {
        // 1.根据 id 查询到题目和题目结果信息（按分数降序排序）
        Long appId = app.getId();
        Question question = questionService.lambdaQuery()
                .eq(Question::getAppId, appId).one();
        List<ScoringResult> scoringResultList = scoringResultService.lambdaQuery()
                .eq(ScoringResult::getAppId, appId)
                .orderByDesc(ScoringResult::getResultScoreRange)
                .list();
        // 2.统计用户的总得分
        QuestionVO questionVO = QuestionVO.objToVo(question);
        List<QuestionContentDTO> questionContentDTOList = questionVO.getQuestionContent();
        // 初始化一个int，用于统计总得分
        int totalScore = 0;
        int choicesNum = choices.size();
        int questionNum = questionContentDTOList.size();
        // 遍历每一道题
        for (int i = 0; i <Math.min(choicesNum, questionNum); i++) {
            // 获取第 i 道 question
            QuestionContentDTO questionContentDTO = questionContentDTOList.get(i);
            // 获取第 i 个 choice
            String choice = choices.get(i);
            // 获取第 i 道 question 的选项
            List<QuestionContentDTO.Option> optionList = questionContentDTO.getOptions();
            // 遍历题目选项列表
            for (QuestionContentDTO.Option option : optionList) {
                // 判断用户选的是哪个选项
                if (option.getKey().equals(choice)) {
                    // 获取选项对应的score
                    Integer score = Optional.of(option.getScore()).orElse(0);
                    // 累加总得分
                    totalScore += score;
                    break;
                }
            }
        }
        // 3.遍历得分结果，找到第一个用户分数大于得分范围的结果，作为最终结果
        // 初始化最高分数对应的评分结果
        ScoringResult maxScoringResult = scoringResultList.get(0);
        for (ScoringResult scoringResult : scoringResultList) {
            // 如果实际总得分大于该得分结果的结果得分范围（之前以按结果得分范围降序排列），则该总得分就在该得分结果对应的分数区间内
            if(totalScore >= scoringResult.getResultScoreRange()){
                maxScoringResult = scoringResult;
                break;
            }
        }
        // 4.构造返回值，填充答案对象的属性。
        UserAnswer userAnswer = new UserAnswer();
        userAnswer.setAppId(appId);
        userAnswer.setAppType(app.getAppType());
        userAnswer.setScoringStrategy(app.getScoringStrategy());
        userAnswer.setChoices(JSONUtil.toJsonStr(choices));
        userAnswer.setResultId(maxScoringResult.getId());
        userAnswer.setResultName(maxScoringResult.getResultName());
        userAnswer.setResultDesc(maxScoringResult.getResultDesc());
        userAnswer.setResultPicture(maxScoringResult.getResultPicture());
        userAnswer.setResultScore(totalScore);
        return userAnswer;
    }
}
