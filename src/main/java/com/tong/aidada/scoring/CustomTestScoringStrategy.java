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

/**
 * 自定义测评类应用评分策略
 */
@ScoringStrategyConfig(appType = 1, scoringStrategy = 0)
public class CustomTestScoringStrategy implements ScoringStrategy {

    @Resource
    private QuestionService questionService;

    @Resource
    private ScoringResultService scoringResultService;

    @Override
    public UserAnswer doScore(List<String> choices, App app) throws Exception {
        // 1.根据 id 查询到题目和题目结果信息
        Long appId = app.getId();
        Question question = questionService.lambdaQuery()
                .eq(Question::getAppId, appId)
                .one();
        List<ScoringResult> scoringResultList = scoringResultService.lambdaQuery()
                .eq(ScoringResult::getAppId, appId)
                .list();
        // 2.统计用户每个选择对应的属性个数，如 I = 10 个，E = 5 个
        QuestionVO questionVO = QuestionVO.objToVo(question);
        List<QuestionContentDTO> questionContentDTOList = questionVO.getQuestionContent();
        // 初始化一个Map，用于统计每个选项的个数，key：选项的result，value：个数
        HashMap<String, Integer> resultCount = new HashMap<>();
        // 遍历题目列表
        for (QuestionContentDTO questionContentDTO : questionContentDTOList) {
            // 遍历用户选择列表
            for (String choice : choices) {
                // 获取每道题目的选项
                List<QuestionContentDTO.Option> optionList = questionContentDTO.getOptions();
                // 遍历题目选项列表
                for (QuestionContentDTO.Option option : optionList) {
                    // 判断用户选的是哪个选项
                    if (option.getKey().equals(choice)) {
                        // 获取选项对应的result
                        String result = option.getResult();
                        // 如果result属性不在resultCount中，则初始化个数为0
                        if (!resultCount.containsKey(result)) {
                            resultCount.put(result, 0);
                        }
                        // 增加resultCount中的个数
                        resultCount.put(result, resultCount.get(result) + 1);
                    }
                }
            }
        }
        // 3.遍历每种评分结果，计算哪个结果的得分更高
        // 初始化最高分数和最高分数对应的评分结果
        int maxScore = 0;
        ScoringResult maxScoringResult = scoringResultList.get(0);
        // 遍历评分结果列表
        for (ScoringResult scoringResult : scoringResultList) {
            // 获取评分结果中的结果属性集合
            String resultPropStr = scoringResult.getResultProp();
            List<String> resultPropList = JSONUtil.toList(resultPropStr, String.class);
            // 计算当前评分结果所有结果属性的个数之和，作为分数
            int score = resultPropList.stream()
                    // 计算当前结果属性的个数
                    .mapToInt(resultProp -> resultCount.getOrDefault(resultProp, 0))
                    .sum();
            // 如果是最高分数，则更新最高分数和最高分数对应的评分结果
            if (score > maxScore) {
                maxScore = score;
                maxScoringResult = scoringResult;
            }
        }
        // 4.构造返回值，填充答案对象的属性
        UserAnswer userAnswer = new UserAnswer();
        userAnswer.setAppId(appId);
        userAnswer.setAppType(app.getAppType());
        userAnswer.setScoringStrategy(app.getScoringStrategy());
        userAnswer.setChoices(JSONUtil.toJsonStr(choices));
        userAnswer.setResultId(maxScoringResult.getId());
        userAnswer.setResultName(maxScoringResult.getResultName());
        userAnswer.setResultDesc(maxScoringResult.getResultDesc());
        userAnswer.setResultPicture(maxScoringResult.getResultPicture());
        return userAnswer;
    }
}
