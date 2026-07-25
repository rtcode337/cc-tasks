package dev.cctasks.mcp;

import java.lang.reflect.Type;

import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

/**
 * 文字列の戻り値を JSON 文字列に包まずそのまま返す。
 *
 * <p>get_task は「そのままプロンプトとして使える Markdown」を返すのが目的なので、
 * 既定のコンバータのように {@code "# タスク #1: ...\n\n..."} とクォート・エスケープ
 * されてしまうと読みにくい。
 */
public class RawTextResultConverter implements ToolCallResultConverter {

    private static final DefaultToolCallResultConverter DELEGATE = new DefaultToolCallResultConverter();

    @Override
    public String convert(Object result, Type returnType) {
        if (result instanceof String text) {
            return text;
        }
        return DELEGATE.convert(result, returnType);
    }
}
