<template>
  <div class="eye-character" ref="characterEl">
    <!-- 对话框在上方 -->
    <div v-if="message" class="speech-bubble" :class="messageType">
      <div class="bubble-content">{{ message }}</div>
      <div class="bubble-tail"></div>
    </div>
    <!-- 人物图片在下方 -->
    <img :src="currentImage" class="character-img" alt="人物" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'

// 人物图片导入（WebP 格式，400px）
import imgNormal from '@/assets/characters/人物正常.webp'
import imgTop from '@/assets/characters/人物看上方.webp'
import imgBottom from '@/assets/characters/人物看下方.webp'
import imgLeft from '@/assets/characters/人物看左方.webp'
import imgRight from '@/assets/characters/人物看右方.webp'
import imgTopLeft from '@/assets/characters/人物看左上方.webp'
import imgTopRight from '@/assets/characters/人物看右上方.webp'
import imgBottomLeft from '@/assets/characters/人物看左下方.webp'
import imgBottomRight from '@/assets/characters/人物看右下方.webp'

defineProps<{
  message?: string
  messageType?: 'info' | 'success' | 'error' | 'warning'
}>()

const mouseX = ref(0)
const mouseY = ref(0)
const characterEl = ref<HTMLElement | null>(null)

// 计算鼠标相对于人物中心的方向
const eyeDirection = computed(() => {
  if (!characterEl.value) return 'normal'

  const rect = characterEl.value.getBoundingClientRect()
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 3 // 眼睛在上1/3处

  const dx = mouseX.value - centerX
  const dy = mouseY.value - centerY

  // 距离太近不切换
  const distance = Math.sqrt(dx * dx + dy * dy)
  if (distance < 100) return 'normal'

  // 计算角度 (atan2 返回弧度，范围 -PI 到 PI)
  const angle = Math.atan2(dy, dx) * (180 / Math.PI)

  // 8个方向，每个方向45度
  if (angle >= -22.5 && angle < 22.5) return 'right'
  if (angle >= 22.5 && angle < 67.5) return 'bottom-right'
  if (angle >= 67.5 && angle < 112.5) return 'bottom'
  if (angle >= 112.5 && angle < 157.5) return 'bottom-left'
  if (angle >= 157.5 || angle < -157.5) return 'left'
  if (angle >= -157.5 && angle < -112.5) return 'top-left'
  if (angle >= -112.5 && angle < -67.5) return 'top'
  if (angle >= -67.5 && angle < -22.5) return 'top-right'

  return 'normal'
})

// 根据方向选择图片
const currentImage = computed(() => {
  const directionMap: Record<string, string> = {
    'normal': imgNormal,
    'top': imgTop,
    'bottom': imgBottom,
    'left': imgLeft,
    'right': imgRight,
    'top-left': imgTopLeft,
    'top-right': imgTopRight,
    'bottom-left': imgBottomLeft,
    'bottom-right': imgBottomRight
  }
  return directionMap[eyeDirection.value] || imgNormal
})

// 鼠标移动监听
const handleMouseMove = (e: MouseEvent) => {
  mouseX.value = e.clientX
  mouseY.value = e.clientY
}

onMounted(() => {
  window.addEventListener('mousemove', handleMouseMove)
})

onUnmounted(() => {
  window.removeEventListener('mousemove', handleMouseMove)
})
</script>

<style scoped lang="scss">
.eye-character {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.character-img {
  width: 200px;
  height: auto;
  filter: drop-shadow(0 4px 12px rgba(0, 0, 0, 0.15));
}

/* 对话框 */
.speech-bubble {
  position: relative;
  margin-bottom: 10px;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
  animation: bubble-in 0.3s ease;
  min-width: 150px;
  max-width: 220px;
  text-align: center;

  &.info {
    background: #fff0f6;
    color: #eb2f96;
    border: 1px solid #ffadd2;
  }

  &.success {
    background: #f0f9eb;
    color: #67c23a;
    border: 1px solid #e1f3d8;
  }

  &.error {
    background: #fef0f0;
    color: #f56c6c;
    border: 1px solid #fde2e2;
  }

  &.warning {
    background: #fdf6ec;
    color: #e6a23c;
    border: 1px solid #faecd8;
  }
}

.bubble-tail {
  position: absolute;
  bottom: -8px;
  left: 50%;
  transform: translateX(-50%);
  width: 0;
  height: 0;
  border-left: 8px solid transparent;
  border-right: 8px solid transparent;

  .speech-bubble.info & {
    border-top: 8px solid #ffadd2;
  }

  .speech-bubble.success & {
    border-top: 8px solid #e1f3d8;
  }

  .speech-bubble.error & {
    border-top: 8px solid #fde2e2;
  }

  .speech-bubble.warning & {
    border-top: 8px solid #faecd8;
  }
}

@keyframes bubble-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 移动端适配 */
@media screen and (max-width: 768px) {
  .character-img {
    width: 140px;
  }

  .speech-bubble {
    min-width: 120px;
    max-width: 180px;
    font-size: 13px;
    padding: 10px 12px;
  }
}
</style>
