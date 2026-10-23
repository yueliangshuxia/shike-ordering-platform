/**
 * 图表的共用主题（ECharts）。
 *
 * 放在一个文件里而不是散在组件中，是为了让两个图看起来是同一套东西：
 * 轴线多粗、网格多淡、字用什么颜色，只在这里定义一次。
 */

export const INK = '#1B2621'
export const INK_2 = '#4A5B54'
export const INK_3 = '#616B65'
export const LINE = '#DFDED7'
export const LINE_2 = '#ECEBE5'
export const PAPER = '#F2F1EC'
export const CARD = '#FFFFFF'
export const PINE = '#1E4034'
export const CINNABAR = '#C4321F'

/**
 * 分类色板。**当前两个图都是单系列，用不到它**，先留在这里备用。
 *
 * 之所以留着：一旦加上「多系列」的图（比如按月堆叠各分类营收），
 * 就需要一套能区分开、且对色觉障碍也分得开的颜色。随便挑六个品牌色
 * 是不行的 —— 本项目的色板（松墨绿、朱砂、金、玉）本身偏灰，
 * 六个一起上会互相分不清。
 *
 * 下面这组是用 dataviz 的校验脚本跑过的，在浅色卡片底（#FFFFFF）上
 * 五项检查全过：
 *   node scripts/validate_palette.js "<下面这串>" --mode light --surface "#FFFFFF"
 *   相邻色最差 CVD ΔE 12.3（deutan）、正常视觉最差 ΔE 16.1、对比度全部 ≥ 3:1
 *
 * **顺序是安全机制的一部分，不要重排** —— 换顺序会改变"哪两个颜色相邻"，
 * 而相邻对才是校验的对象。要加颜色或换颜色，重跑一次那个脚本。
 */
export const CATEGORICAL = ['#C4321F', '#C08A2E', '#2F7FB5', '#25905C', '#8E5AA8', '#B5651D']

/** 坐标轴、网格、提示框的通用配置。recessive —— 网格是背景不是内容 */
export const axisCommon = {
  axisLine: { lineStyle: { color: LINE } },
  axisTick: { show: false },
  axisLabel: { color: INK_3, fontSize: 12 },
}

export const gridCommon = {
  left: 8,
  right: 16,
  top: 16,
  bottom: 8,
  containLabel: true,
}

export const splitLineCommon = {
  lineStyle: { color: LINE_2, type: 'solid' },
}

export const tooltipCommon = {
  backgroundColor: CARD,
  borderColor: LINE,
  borderWidth: 1,
  padding: [8, 12],
  textStyle: { color: INK, fontSize: 12 },
  extraCssText: 'box-shadow: 0 6px 20px rgba(27,38,33,.10); border-radius: 6px;',
}
