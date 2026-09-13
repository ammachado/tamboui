/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.widgets.syntax;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-language smoke tests: every built-in grammar highlights an idiomatic
 * snippet with the expected token types. Guards against typos in keyword
 * lists, wrong rule ordering, and silently broken grammars.
 */
class GrammarSmokeTest {

    private static final Style BASE = Style.EMPTY.fg(Color.GRAY);
    private static final SyntaxTheme THEME = SyntaxTheme.DEFAULTS;
    private static final SyntaxHighlighter HIGHLIGHTER = RegexSyntaxHighlighter.defaults();

    static Stream<Arguments> snippets() {
        return Stream.of(
            Arguments.of("java", "class A { String s = \"x\"; } // done",
                new Object[][] {
                    {"class", TokenType.KEYWORD}, {"\"x\"", TokenType.STRING}, {"// done", TokenType.COMMENT}}),
            Arguments.of("kotlin", "fun greet(name: String) = \"hi\" // done",
                new Object[][] {
                    {"fun", TokenType.KEYWORD}, {"\"hi\"", TokenType.STRING}, {"// done", TokenType.COMMENT}}),
            Arguments.of("javascript", "function f() { return `tpl`; } // done",
                new Object[][] {
                    {"function", TokenType.KEYWORD}, {"`tpl`", TokenType.STRING}, {"// done", TokenType.COMMENT}}),
            Arguments.of("typescript", "interface A { name: string } // done",
                new Object[][] {
                    {"interface", TokenType.KEYWORD}, {"// done", TokenType.COMMENT}}),
            Arguments.of("python", "def f():  # done\n    return 'x'",
                new Object[][] {
                    {"def", TokenType.KEYWORD}, {"# done", TokenType.COMMENT}, {"'x'", TokenType.STRING}}),
            Arguments.of("json", "{\"k\": [1, true]}",
                new Object[][] {
                    {"\"k\"", TokenType.STRING}, {"1", TokenType.NUMBER}, {"true", TokenType.KEYWORD}}),
            Arguments.of("css", "a { color: red; margin: 4px; }",
                new Object[][] {
                    {"4px", TokenType.NUMBER}}),
            Arguments.of("bash", "if [ -f x ]; then echo \"hi\"; fi # done",
                new Object[][] {
                    {"if", TokenType.KEYWORD}, {"\"hi\"", TokenType.STRING}, {"# done", TokenType.COMMENT}}),
            Arguments.of("yaml", "key: true # done",
                new Object[][] {
                    {"true", TokenType.KEYWORD}, {"# done", TokenType.COMMENT}}),
            Arguments.of("sql", "select id from users where age > 42 -- done",
                new Object[][] {
                    {"select", TokenType.KEYWORD}, {"42", TokenType.NUMBER}, {"-- done", TokenType.COMMENT}}),
            Arguments.of("sql", "SELECT id FROM users -- uppercase is the common style",
                new Object[][] {
                    {"SELECT", TokenType.KEYWORD}}),
            Arguments.of("properties", "# note\napp.port=8080\nname=${env}",
                new Object[][] {
                    {"# note", TokenType.COMMENT}, {"app.port", TokenType.ATTRIBUTE},
                    {"8080", TokenType.NUMBER}, {"${env}", TokenType.CONSTANT}}),
            Arguments.of("go", "func main() { s := \"x\" } // done",
                new Object[][] {
                    {"func", TokenType.KEYWORD}, {"\"x\"", TokenType.STRING}, {"// done", TokenType.COMMENT}}),
            Arguments.of("rust", "fn main() { let s = \"x\"; } // done",
                new Object[][] {
                    {"fn", TokenType.KEYWORD}, {"\"x\"", TokenType.STRING}, {"// done", TokenType.COMMENT}}));
    }

    @ParameterizedTest(name = "{0}: ''{1}''")
    @MethodSource("snippets")
    void grammarHighlightsIdiomaticSnippet(String language, String snippet, Object[][] expectations) {
        List<Line> lines = HIGHLIGHTER.highlight(snippet, language, BASE, THEME);

        for (Object[] expectation : expectations) {
            String content = (String) expectation[0];
            TokenType type = (TokenType) expectation[1];
            assertThat(spanColor(lines, content))
                .as("span '%s' in %s snippet should be %s", content, language, type)
                .isEqualTo(THEME.style(type, BASE).fg().map(Color::toRgb).orElse(null));
        }
    }

    private static Color.Rgb spanColor(List<Line> lines, String content) {
        for (Line line : lines) {
            for (Span span : line.spans()) {
                if (span.content().equals(content)) {
                    return span.style().fg().map(Color::toRgb).orElse(null);
                }
            }
        }
        return null;
    }
}
