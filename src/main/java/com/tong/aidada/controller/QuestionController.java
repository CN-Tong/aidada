package com.tong.aidada.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tong.aidada.annotation.AuthCheck;
import com.tong.aidada.common.BaseResponse;
import com.tong.aidada.common.DeleteRequest;
import com.tong.aidada.common.ErrorCode;
import com.tong.aidada.common.ResultUtils;
import com.tong.aidada.constant.UserConstant;
import com.tong.aidada.exception.BusinessException;
import com.tong.aidada.exception.ThrowUtils;
import com.tong.aidada.manager.AiManager;
import com.tong.aidada.model.dto.question.*;
import com.tong.aidada.model.entity.App;
import com.tong.aidada.model.entity.Question;
import com.tong.aidada.model.entity.User;
import com.tong.aidada.model.enums.AppTypeEnum;
import com.tong.aidada.model.vo.QuestionVO;
import com.tong.aidada.service.AppService;
import com.tong.aidada.service.QuestionService;
import com.tong.aidada.service.UserService;
import com.zhipu.oapi.service.v4.model.ModelData;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 题目接口
 */
@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {

    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;

    @Resource
    private AppService appService;

    @Resource
    private AiManager aiManager;

    // region 增删改查

    /**
     * 创建题目
     *
     * @param questionAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(questionAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionAddRequest, question);
        List<QuestionContentDTO> questionContent = questionAddRequest.getQuestionContent();
        question.setQuestionContent(JSONUtil.toJsonStr(questionContent));
        // 数据校验
        questionService.validQuestion(question, true);
        // 填充默认值
        User loginUser = userService.getLoginUser(request);
        question.setUserId(loginUser.getId());
        // 写入数据库
        boolean result = questionService.save(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 返回新写入的数据 id
        long newQuestionId = question.getId();
        return ResultUtils.success(newQuestionId);
    }

    /**
     * 删除题目
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestion.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新题目（仅管理员可用）
     *
     * @param questionUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        if (questionUpdateRequest == null || questionUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionUpdateRequest, question);
        List<QuestionContentDTO> questionContent = questionUpdateRequest.getQuestionContent();
        question.setQuestionContent(JSONUtil.toJsonStr(questionContent));
        // 数据校验
        questionService.validQuestion(question, false);
        // 判断是否存在
        long id = questionUpdateRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取题目（封装类）
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<QuestionVO> getQuestionVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Question question = questionService.getById(id);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVO(question, request));
    }

    /**
     * 分页获取题目列表（仅管理员可用）
     *
     * @param questionQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Question>> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionPage);
    }

    /**
     * 分页获取题目列表（封装类）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                               HttpServletRequest request) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 分页获取当前登录用户创建的题目列表
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        questionQueryRequest.setUserId(loginUser.getId());
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 编辑题目（给用户使用）
     *
     * @param questionEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest, HttpServletRequest request) {
        if (questionEditRequest == null || questionEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionEditRequest, question);
        List<QuestionContentDTO> questionContent = questionEditRequest.getQuestionContent();
        question.setQuestionContent(JSONUtil.toJsonStr(questionContent));
        // 数据校验
        questionService.validQuestion(question, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = questionEditRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldQuestion.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    // endregion

    // region AI生成题目功能

    // 系统Prompt
    private static final String GENERATE_QUESTION_SYSTEM_MESSAGE = "你是一位严谨的出题专家，我会给你如下信息：\n" +
            "```\n" +
            "应用名称 appName，\n" +
            "【【【应用描述 appDesc】】】，\n" +
            "应用类别 appType，\n" +
            "要生成的题目数 titleNum，\n" +
            "每个题目的选项数 optionNum\n" +
            "```\n" +
            "\n" +
            "请你根据上述信息，按照以下步骤来出题：\n" +
            "1. 严格按照下面的 json 格式输出题目和选项\n" +
            "```\n" +
            "[{\"options\":[{\"result\":\"选项属性，例如 MBTI E/I、S/N、T/F、J/P中的E\",\"score\":10,\"value\":\"选项内容\",\"key\":\"选项标识，例如 A\"},{\"result\":\"选项属性，例如 MBTI E/I、S/N、T/F、J/P中的I\",\"score\":0,\"value\":\"选项内容\",\"key\":\"选项标识，例如 B\"}],\"title\":\"题目标题\"},{\"options\":[{\"result\":\"选项属性，例如 MBTI E/I、S/N、T/F、J/P中的T\",\"score\":0,\"value\":\"选项内容\",\"key\":\"选项标识，例如 A\"},{\"result\":\"选项属性，例如 MBTI E/I、S/N、T/F、J/P中的F\",\"score\":20,\"value\":\"选项内容\",\"key\":\"选项标识，例如 B\"}],\"title\":\"题目标题\"}]\n" +
            "```\n" +
            "其中 title 是题目，必须帮我提供。options 是题目对应的选项，一个 title 必须有 titleNum 个 options。key 是选项标识，按照英文字母序（比如 A、B、C、D）以此类推，必须帮我提供。value 是选项内容，必须帮我提供，答题者会根据选项内容判断是否选择该项。如果 appType 是```测评类```则 result 必须帮我提供，score 设置为 0。如果 appType 是```得分类```则 score 必须帮我提供，result 设置为 \"\"。\n" +
            "2. 根据 appName 和 appDesc 生成 titleNum 个 title。title 尽可能地短，不能重复。appType 为```测评类```的题目以开放题为主，appType 为```得分类```的题目以客观题为主。\n" +
            "3. 严格根据我提供的 optionNum 按英文字母序生成 key\n" +
            "4. 如果 appType 是```测评类```，则为每个 key 生成 value，每个 value 均为对题目的主观回答。value 尽可能短。\n" +
            "5. 如果 appType 是```得分类```，则回答一遍 title，得到每道 title 在数学上和逻辑上的正确答案，并分配给对应 title 某个随机的 key 的 value。随后为该 title 的其他 value 分配错误答案。value 尽可能短，每个 title 的选项不能重复。\n" +
            "6. 检查题目是否包含序号，若包含序号则去除序号。\n" +
            "7. 返回的题目列表格式必须为 JSON 数组。";

    /**
     * 生成题目的用户消息
     *
     * @param app
     * @param questionNumber
     * @param optionNumber
     * @return
     */
    private String getGenerateQuestionUserMessage(App app, int questionNumber, int optionNumber) {
        StringBuilder userMessage = new StringBuilder();
        userMessage.append(app.getAppName()).append("\n");
        userMessage.append(app.getAppDesc()).append("\n");
        userMessage.append(AppTypeEnum.getEnumByValue(app.getAppType()).getText() + "类").append("\n");
        userMessage.append(questionNumber).append("\n");
        userMessage.append(optionNumber);
        return userMessage.toString();
    }

    /**
     * AI生成题目内容
     *
     * @param aiGenerateQuestionRequest
     * @return
     */
    @PostMapping("/ai_generate")
    public BaseResponse<List<QuestionContentDTO>> aiGenerateQuestion(
            @RequestBody AiGenerateQuestionRequest aiGenerateQuestionRequest) {
        // 校验aiGenerateQuestionRequest
        ThrowUtils.throwIf(aiGenerateQuestionRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取参数
        Long appId = aiGenerateQuestionRequest.getAppId();
        int questionNumber = aiGenerateQuestionRequest.getQuestionNumber();
        int optionNumber = aiGenerateQuestionRequest.getOptionNumber();
        // 获取应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 封装 Prompt
        String userMessage = getGenerateQuestionUserMessage(app, questionNumber, optionNumber);
        // AI生成（通用不稳定同步请求）
        String result = aiManager.doSyncUnstableRequest(GENERATE_QUESTION_SYSTEM_MESSAGE, userMessage);
        // 截取[]及其内部
        int start = result.indexOf("[");
        int end = result.lastIndexOf("]");
        String questionContentStr = result.substring(start, end + 1);
        System.out.println("成功生成题目内容：" + questionContentStr);
        // json转questionContentDTO列表
        try {
            List<QuestionContentDTO> questionContentDTOList = JSONUtil.toList(questionContentStr, QuestionContentDTO.class);
            return ResultUtils.success(questionContentDTOList);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成的题目格式错误，请减少题目数量和选项数量，或修改应用名称和应用描述后重试");
        }
    }

    /**
     * AI实时生成题目内容
     *
     * @param aiGenerateQuestionRequest
     * @return
     */
    @GetMapping("/ai_generate/sse")
    public SseEmitter aiGenerateQuestionSSE(AiGenerateQuestionRequest aiGenerateQuestionRequest) {
        // 校验aiGenerateQuestionRequest
        ThrowUtils.throwIf(aiGenerateQuestionRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取参数
        Long appId = aiGenerateQuestionRequest.getAppId();
        int questionNumber = aiGenerateQuestionRequest.getQuestionNumber();
        int optionNumber = aiGenerateQuestionRequest.getOptionNumber();
        // 获取应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 封装 Prompt
        String userMessage = getGenerateQuestionUserMessage(app, questionNumber, optionNumber);
        // 建立 SSE 连接对象，0表示永不超时
        SseEmitter sseEmitter = new SseEmitter(0L);
        // 调用 AI 获取数据流（通用流式同步请求）
        Flowable<ModelData> modelDataFlowable = aiManager.doStreamUnstableRequest(
                GENERATE_QUESTION_SYSTEM_MESSAGE, userMessage);
        // 左括号计数器，是一个元子类，除了默认值外，当回归为 0 时，表示左括号等于右括号，可以截取
        AtomicInteger counter = new AtomicInteger(0);
        // 用于拼接完整题目
        StringBuilder contentBuilder = new StringBuilder();
        modelDataFlowable
                // 异步线程池执行
                .observeOn(Schedulers.io())
                // 拿到每个数据
                .map(modelData -> modelData.getChoices().get(0).getDelta().getContent())
                // 特殊字符过滤为空字符
                .map(message -> message.replaceAll("\\s", ""))
                // 过滤掉空字符
                .filter(StrUtil::isNotBlank)
                // 分流，将字符串转换成字符
                .flatMap(message -> {
                    ArrayList<Character> characterList = new ArrayList<>();
                    for (char c : message.toCharArray()) {
                        characterList.add(c);
                    }
                    return Flowable.fromIterable(characterList);
                })
                // 括号匹配算法，拿到生成的每一道题目
                .doOnNext(c -> {
                    System.out.print(c);
                    // 如果字符是 {，计数器 + 1
                    if (c == '{') {
                        counter.addAndGet(1);
                    }
                    // 如果 counter 不为 0，则不截取，拼接题目
                    if (counter.get() > 0) {
                        contentBuilder.append(c);
                    }
                    // 如果字符是 }，计数器 - 1
                    if (c == '}') {
                        counter.addAndGet(-1);
                        // 如果 counter 为 0，拼接题目，通过 SSE 返回给前端
                        if (counter.get() == 0) {
                            sseEmitter.send(JSONUtil.toJsonStr(contentBuilder.toString()));
                            // 重置，准备拼接下一题
                            contentBuilder.setLength(0);
                        }
                    }
                })
                .doOnError(e -> log.error("SSE error", e))
                // 完成后告诉前端
                .doOnComplete(sseEmitter::complete)
                .subscribe();
        return sseEmitter;
    }

    // endregion
}
