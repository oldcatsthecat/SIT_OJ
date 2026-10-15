// src/utils/latex.js
import katex from 'katex';
import 'katex/dist/katex.min.css';

export const renderInlineLatex = (text) => {
    if (!text || typeof text !== 'string') return text;

    // 仅替换被 $ 包裹的内容
    return text.replace(/\$(.*?)\$/g, (match, formula) => {
        try {
            return katex.renderToString(formula, {
                throwOnError: false,
                displayMode: false
            });
        } catch (e) {
            return match; // 出错则返回原样
        }
    });
};