<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '@/stores/cart'
import { useMenuStore } from '@/stores/menu'
import { money, spiceLabel, tagMeta } from '@/utils/format'
import { dishPlaceholder } from '@/utils/placeholder'

/**
 * 菜单上的一张菜品卡。
 *
 * 结构和类名直接沿用 demo 的 `app.js` dishCard()（`.dish` / `.dish-figure` /
 * `.dish-flag` / `.dish-foot` / `.add-btn`），样式由 style.css 提供。
 */
const props = defineProps({
  dish: { type: Object, required: true },
  /** 后厨屏等场景不需要加购按钮 */
  actionable: { type: Boolean, default: true },
})

const emit = defineEmits(['added'])

const router = useRouter()
const cart = useCartStore()
const menu = useMenuStore()

const img = computed(() => menu.imageUrl(props.dish) || dishPlaceholder(props.dish.name))
const soldOut = computed(() => props.dish.soldOut === true)

// 「招牌」在卡片上已经是左上角的旗标了，标签行里不要再重复一遍
const tags = computed(() => (props.dish.tags || []).filter((t) => t !== '招牌'))

function open() {
  router.push({ name: 'dish', params: { code: props.dish.code } })
}

/**
 * 快速加购。
 *
 * 没有规格的菜直接加一行；有规格的菜**不能**在这里替他选 ——
 * 那样用户永远拿不到「大份」，而菜单上看不出差别。
 * 所以跳到详情页让他选。
 */
function quickAdd() {
  if (soldOut.value) return
  const specs = props.dish.specs || []
  if (specs.length > 1) {
    open()
    return
  }
  cart.add(props.dish, specs[0] ?? null, 1)
  emit('added', props.dish)
}
</script>

<template>
  <article class="dish" :class="{ 'is-sold': soldOut }">
    <a class="dish-figure" href="#" :aria-label="`查看 ${dish.name} 详情`" @click.prevent="open">
      <img :src="img" :alt="dish.name" loading="lazy" />
      <span v-if="soldOut" class="dish-flag is-sold">今日售罄</span>
      <span v-else-if="dish.tags?.includes('招牌')" class="dish-flag">招牌</span>
    </a>

    <div class="dish-body">
      <h3 class="dish-name">
        <a href="#" @click.prevent="open">{{ dish.name }}</a>
      </h3>
      <p class="dish-desc">{{ dish.brief }}</p>

      <div class="dish-tags">
        <span v-if="dish.spice" class="tag tag-hot">
          <iconify-icon icon="mdi:chili-hot" width="12" height="12" aria-hidden="true"></iconify-icon>
          {{ spiceLabel(dish.spice) }}
        </span>
        <span v-for="t in tags" :key="t" class="tag" :class="tagMeta(t).cls">
          <iconify-icon :icon="tagMeta(t).icon" width="12" height="12" aria-hidden="true"></iconify-icon>
          {{ t }}
        </span>
      </div>

      <div class="dish-foot">
        <span class="price"><span class="cur">¥</span>{{ money(dish.price) }}</span>
        <span class="sold">月售 {{ dish.sold }}</span>
        <button
          v-if="actionable"
          class="add-btn"
          type="button"
          :disabled="soldOut"
          :aria-label="soldOut ? '已售罄' : `加入点单：${dish.name}`"
          @click.stop="quickAdd"
        >
          <iconify-icon :icon="soldOut ? 'mdi:close' : 'mdi:plus'" width="18" height="18"></iconify-icon>
        </button>
      </div>
    </div>
  </article>
</template>
