package com.ss.aianki;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;

import okhttp3.ResponseBody;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Streaming;
import io.reactivex.Flowable;

public interface OpenAiApi {
    @Streaming
    @POST("v1/chat/completions")
    Flowable<ResponseBody> streamChatCompletion(@Body ChatCompletionRequest request);
} 