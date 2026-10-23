/**
 * 菜品占位图。
 *
 * 有些菜没有实拍图（后端 `img` 字段为空）。这里生成一张带菜名的虚线图块，
 * 比塞一张随机风景图诚实得多 —— 一眼就能看出「这里要换图」，
 * 而随机图会被当成真的菜品照片，答辩时很难解释。
 *
 * 颜色取自设计系统的令牌值，改 style.css 时这里要跟着改
 * （data URI 里没法用 CSS 变量）。
 */
const PAPER_2 = '#E9E8E1'
const LINE = '#C7C6BD'
const INK_2 = '#4A5B54'
const INK_3 = '#616B65'

export function dishPlaceholder(name) {
  const safe = String(name ?? '').slice(0, 8)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 450">
<rect width="600" height="450" fill="${PAPER_2}"/>
<rect x="17" y="17" width="566" height="416" fill="none" stroke="${LINE}" stroke-width="2" stroke-dasharray="11 9"/>
<circle cx="300" cy="186" r="34" fill="none" stroke="${LINE}" stroke-width="2"/>
<path d="M288 186h24M300 174v24" stroke="${LINE}" stroke-width="2" stroke-linecap="round"/>
<text x="300" y="262" text-anchor="middle" font-family="Noto Serif SC, SimSun, serif" font-size="40" fill="${INK_2}">${escapeXml(safe)}</text>
<text x="300" y="300" text-anchor="middle" font-family="Noto Sans SC, Microsoft YaHei, sans-serif" font-size="18" fill="${INK_3}">占位图 · 待换成实拍</text>
</svg>`
  return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg)
}

/** 菜名会进 SVG 文本节点，必须转义，否则 `&` 这类字符会让整张图解析失败 */
function escapeXml(s) {
  return s.replace(/[&<>"']/g, (c) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&apos;',
  })[c])
}
