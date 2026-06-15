<template>
  <div class="electricity-page">
    <StatusBar />
    <div class="status-bar-placeholder"></div>

    <div class="detail-container">
      <div class="detail-header">
        <el-button class="back-btn" @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <h2 class="page-title">电费详情</h2>
        <span v-if="history.roomName" class="room-badge">{{ history.roomName }}</span>
        <el-button v-if="isAdmin" class="collect-btn" type="warning" plain size="small" :loading="collecting" @click="handleCollect">
          手动采集
        </el-button>
      </div>

      <div v-if="loading" class="loading-wrapper">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>

      <template v-else>
        <!-- 统计卡片 -->
        <div class="stats-row">
          <div class="stat-card">
            <div class="stat-label">总耗电</div>
            <div class="stat-value">{{ history.totalConsumption ?? '--' }} <span class="unit">度</span></div>
          </div>
          <div class="stat-card">
            <div class="stat-label">个人日均</div>
            <div class="stat-value personal">{{ history.avgPersonal ?? '--' }} <span class="unit">度</span></div>
          </div>
          <div class="stat-card">
            <div class="stat-label">楼栋日均</div>
            <div class="stat-value building">{{ history.avgBuilding ?? '--' }} <span class="unit">度</span></div>
          </div>
          <div class="stat-card">
            <div class="stat-label">当前余额</div>
            <div class="stat-value balance">{{ currentBalance ?? '--' }} <span class="unit">度</span></div>
          </div>
        </div>

        <!-- 时间范围切换 -->
        <div class="range-switch">
          <el-button-group>
            <el-button :type="days === 7 ? 'primary' : 'default'" @click="days = 7; fetchHistory()">近7天</el-button>
            <el-button :type="days === 30 ? 'primary' : 'default'" @click="days = 30; fetchHistory()">近30天</el-button>
          </el-button-group>
        </div>

        <!-- 折线图 -->
        <div class="chart-wrapper">
          <div ref="chartRef" class="chart-container"></div>
        </div>

        <!-- 排行榜 -->
        <div v-if="ranking" class="ranking-section">
          <div class="ranking-header">
            <h3 class="section-title">楼栋用电排行（{{ ranking.date }}）</h3>
            <el-button-group>
              <el-button :type="rankingType === 'top' ? 'primary' : 'default'" size="small" @click="switchRanking('top')">耗电最高</el-button>
              <el-button :type="rankingType === 'bottom' ? 'primary' : 'default'" size="small" @click="switchRanking('bottom')">耗电最低</el-button>
            </el-button-group>
          </div>
          <div v-if="ranking.list && ranking.list.length > 0" class="ranking-list">
            <div v-for="(item, index) in ranking.list" :key="item.room_name" class="ranking-item" :class="{ 'my-room': item.room_name === history.roomName }">
              <span class="rank-badge" :class="Number(index) < 3 ? 'rank-' + (Number(index) + 1) : ''">{{ Number(index) + 1 }}</span>
              <span class="room-name">{{ item.room_name }}</span>
              <span class="consumption">{{ item.consumption }} 度</span>
              <span v-if="item.room_name === history.roomName" class="my-tag">我的宿舍</span>
            </div>
          </div>
          <div v-else class="ranking-empty">暂无数据</div>
          <div v-if="ranking.myRanking && !isInRankingList" class="ranking-list" style="margin-top: 8px">
            <div class="ranking-item my-room">
              <span class="rank-badge">{{ ranking.myRanking.rank }}</span>
              <span class="room-name">{{ ranking.myRanking.roomName }} <small>我的宿舍</small></span>
              <span class="consumption">{{ ranking.myRanking.consumption }} 度</span>
            </div>
          </div>
        </div>

        <!-- 充值记录 -->
        <div v-if="recharges.length > 0" class="recharge-section">
          <h3 class="section-title">充值记录</h3>
          <div class="recharge-list">
            <div v-for="r in recharges" :key="r.record_date" class="recharge-item" :class="{ unconfirmed: !r.confirmed }">
              <div class="recharge-date">{{ r.record_date }}</div>
              <div class="recharge-info">
                <span v-if="r.confirmed">
                  充值 {{ r.kwh }} 度
                  <span v-if="r.amount">（{{ r.amount }} 元</span>
                  <span v-if="r.price">，{{ r.price }} 元/度）</span>
                  <span v-else-if="r.amount">）</span>
                </span>
                <span v-else class="unconfirmed-text">
                  余额增加 {{ r.kwh }} 度（待确认）
                </span>
              </div>
              <el-button v-if="!r.confirmed" type="primary" size="small" @click="openRechargeDialog(r, false)">
                补充信息
              </el-button>
              <el-button v-if="r.confirmed" type="default" size="small" @click="openRechargeDialog(r, true)">
                编辑
              </el-button>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 充值弹窗 -->
    <el-dialog v-model="rechargeDialogVisible" :title="editMode ? '修改充值信息' : '补充充值信息'" width="400px">
      <div class="recharge-form">
        <div class="form-item">
          <label>充值日期</label>
          <el-input :value="currentRecharge?.record_date" disabled />
        </div>
        <div class="form-item">
          <label>充值金额（元）</label>
          <el-input-number v-model="rechargeForm.amount" :min="0.01" :step="10" :precision="2" />
        </div>
        <div class="form-item">
          <label>电价（元/度）</label>
          <el-input-number v-model="rechargeForm.price" :min="0.01" :step="0.01" :precision="4" />
        </div>
        <div class="form-item" v-if="rechargeForm.amount > 0 && rechargeForm.price > 0">
          <label>充值度数</label>
          <div class="calculated-amount">{{ (rechargeForm.amount / rechargeForm.price).toFixed(2) }} 度</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="rechargeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRecharge">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ArrowLeft, Loading } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusBar from '@/components/StatusBar.vue'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'
import * as echarts from 'echarts'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()

function accentRgba(alpha: number) {
  const hex = themeStore.config.theme.color
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgba(${r},${g},${b},${alpha})`
}

const userStore = useUserStore()
const isAdmin = computed(() => userStore.userInfo?.username === 'admin' || userStore.userInfo?.role === 'admin')

const loading = ref(true)
const days = ref(7)
const chartRef = ref<HTMLElement>()
const currentBalance = ref<number | null>(null)
const history = ref<any>({
  personal: [],
  buildingAvg: [],
  recharges: [],
  totalConsumption: null,
  avgPersonal: null,
  avgBuilding: null,
  roomName: null
})
const recharges = ref<any[]>([])

const rechargeDialogVisible = ref(false)
const currentRecharge = ref<any>(null)
const editMode = ref(false)
const rechargeForm = ref({
  amount: 0,
  price: 0.55
})

const ranking = ref<any>(null)
const rankingType = ref('top')
const collecting = ref(false)
const isInRankingList = computed(() => {
  if (!ranking.value?.list || !history.value.roomName) return false
  return ranking.value.list.some((item: any) => item.room_name === history.value.roomName)
})

let chart: echarts.ECharts | null = null

const fetchRealtimeBalance = async () => {
  try {
    const res: any = await request.get('/electricity/realtime')
    if (res.code === 200 && res.data) {
      currentBalance.value = res.data.balance
      await nextTick()
      renderChart()
    }
  } catch {
    // ignore
  }
}

const fetchRanking = async (type: string) => {
  try {
    const res: any = await request.get('/electricity/ranking', { params: { type } })
    if (res.code === 200 && res.data) {
      ranking.value = res.data
    }
  } catch {
    // ignore
  }
}

const switchRanking = (type: string) => {
  rankingType.value = type
  fetchRanking(type)
}

const handleCollect = async () => {
  try {
    await ElMessageBox.confirm(
      '将采集所有楼栋的最新电费数据并更新排行榜，确认执行？',
      '手动采集',
      { confirmButtonText: '确认采集', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  collecting.value = true
  try {
    const res: any = await request.post('/electricity/collect')
    if (res.code === 200) {
      ElMessage.success('采集任务已触发，请稍后刷新页面查看结果')
    } else {
      ElMessage.error(res.message || '触发采集失败')
    }
  } catch (err: any) {
    ElMessage.error(err.message || '触发采集失败')
  } finally {
    collecting.value = false
  }
}

const fetchHistory = async () => {
  chart?.dispose()
  chart = null
  loading.value = true
  try {
    const res: any = await request.get('/electricity/history', { params: { days: days.value } })
    if (res.code === 200 && res.data) {
      history.value = res.data
      recharges.value = res.data.recharges || []
    }
  } catch {
    // ignore
  } finally {
    loading.value = false
    await nextTick()
    renderChart()
  }
}

const renderChart = () => {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }

  const personalData: any[] = history.value.personal || []
  const buildingData: any[] = history.value.buildingAvg || []
  const rechargeData: any[] = history.value.recharges || []

  // 生成完整的日期范围：从 days 天前到明天（含明天，用于实时余额预览）
  const today = new Date()
  const dates: string[] = []
  for (let i = days.value - 1; i >= -1; i--) {
    const d = new Date(today)
    d.setDate(d.getDate() - i)
    const yyyy = d.getFullYear()
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    dates.push(yyyy + '-' + mm + '-' + dd)
  }
  // 按record_date做Map索引
  const personalMap = new Map(personalData.map(function(r: any) { return [r.record_date, r] }))
  const buildingMap = new Map(buildingData.map(function(r: any) { return [r.record_date, Number(r.avg_consumption)] }))
  const rechargeMap = new Map(rechargeData.map(function(r: any) { return [r.record_date, r] }))

  // 对齐到完整日期轴
  const consumptionValues = dates.map(function(d: string) {
    var r = personalMap.get(d)
    return r && r.consumption != null ? Number(r.consumption) : null
  })
  const buildingValues = dates.map(function(d: string) {
    return buildingMap.get(d) ?? null
  })

  // 余额线：数据库数据 + 今日实时余额（融入同一条线）
  const balanceValues = dates.map(function(d: string, i: number) {
    const r = personalMap.get(d)
    if (r) return Number(r.balance)
    // 今日无数据库记录，用实时余额补上
    if (i === dates.length - 1 && currentBalance.value != null) return currentBalance.value
    return null
  })
  // 今日实时余额的样式标记（最后一个点用不同样式突出显示）
  const balanceMarkData: any[] = []
  if (currentBalance.value != null && !personalMap.get(dates[dates.length - 1])) {
    balanceMarkData.push({
      coord: [dates[dates.length - 1], currentBalance.value],
      value: currentBalance.value,
      symbol: 'circle',
      symbolSize: 10,
      itemStyle: { color: '#67c23a', borderColor: '#fff', borderWidth: 2 }
    })
  }

  // 充值标记数据
  const rechargeMarkers = dates.map(function(d: string) {
    const r = rechargeMap.get(d)
    if (r) {
      const consumption = personalMap.get(d)
      const val = consumption && consumption.consumption != null ? Number(consumption.consumption) : 0
      return {
        value: val,
        itemStyle: {
          color: r.confirmed ? '#e6a23c' : '#f56c6c',
          borderColor: '#fff',
          borderWidth: 2
        }
      }
    }
    return null
  })

  chart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: function(params: any) {
        var dateStr = params[0].axisValue
        var fullDate = dates.find(function(d: string) { return d.substring(5) === dateStr })
        var html = '<div style="font-weight:600;margin-bottom:4px">' + dateStr + '</div>'
        params.forEach(function(p: any) {
          if (p.seriesName === '充值') return
          var val = (typeof p.value === 'object') ? p.value.value : p.value
          if (val != null) {
            html += '<div>' + p.marker + ' ' + p.seriesName + ': <b>' + val + '</b> 度</div>'
          }
        })
        if (fullDate) {
          var r: any = rechargeMap.get(fullDate)
          if (r) {
            var status = r.confirmed ? '' : ' (待确认)'
            html += '<div style="color:#e6a23c">⚡ 充值: ' + r.kwh + ' 度' + status + '</div>'
          }
        }
        return html
      }
    },
    legend: {
      data: ['每日耗电', '楼栋平均', '每日余额'],
      bottom: days.value > 7 ? 36 : 0
    },
    grid: {
      left: 50,
      right: 50,
      top: 20,
      bottom: days.value > 7 ? 70 : 40
    },
    xAxis: {
      type: 'category',
      data: dates.map(function(d: string) { return d.substring(5) }),
      axisLabel: {
        fontSize: 11,
        interval: 0
      }
    },
    dataZoom: days.value > 7 ? [
      {
        type: 'inside',
        startValue: dates.length - 8,
        endValue: dates.length - 1,
        minValueSpan: 6
      },
      {
        type: 'slider',
        start: Math.max(0, 100 - 800 / dates.length),
        end: 100,
        height: 20,
        bottom: 8,
        borderColor: 'transparent',
        backgroundColor: '#f5f7fa',
        fillerColor: accentRgba(0.15),
        handleStyle: { color: themeStore.config.theme.color }
      }
    ] : undefined,
    yAxis: [
      {
        type: 'value',
        name: '耗电(度)',
        position: 'left',
        axisLabel: { fontSize: 11, color: themeStore.config.theme.color },
        nameTextStyle: { color: themeStore.config.theme.color }
      },
      {
        type: 'value',
        name: '余额(度)',
        position: 'right',
        axisLabel: { fontSize: 11, color: '#67c23a' },
        nameTextStyle: { color: '#67c23a' },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '每日耗电',
        type: 'line',
        yAxisIndex: 0,
        data: consumptionValues,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2, color: themeStore.config.theme.color },
        itemStyle: { color: themeStore.config.theme.color },
        areaStyle: { color: accentRgba(0.1) },
        connectNulls: true
      },
      {
        name: '楼栋平均',
        type: 'line',
        yAxisIndex: 0,
        data: buildingValues,
        smooth: true,
        symbol: 'diamond',
        symbolSize: 6,
        lineStyle: { width: 2, color: '#909399', type: 'dashed' },
        itemStyle: { color: '#909399' },
        connectNulls: true
      },
      {
        name: '每日余额',
        type: 'line',
        yAxisIndex: 1,
        data: balanceValues,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2, color: '#67c23a' },
        itemStyle: { color: '#67c23a' },
        connectNulls: true,
        markPoint: balanceMarkData.length > 0 ? {
          data: balanceMarkData,
          label: { show: true, formatter: '{c}', position: 'top', fontSize: 11 }
        } : undefined
      },
      {
        name: '充值',
        type: 'scatter',
        yAxisIndex: 0,
        data: rechargeMarkers,
        symbolSize: 12,
        z: 10
      }
    ]
  })
}

const openRechargeDialog = (recharge: any, isEdit: boolean) => {
  currentRecharge.value = recharge
  editMode.value = isEdit
  rechargeForm.value = {
    amount: recharge.amount ? Number(recharge.amount) : 0,
    price: recharge.price ? Number(recharge.price) : 0.55
  }
  rechargeDialogVisible.value = true
}

const submitRecharge = async () => {
  const { amount, price } = rechargeForm.value
  if (!amount || !price) {
    ElMessage.warning('请填写充值金额和电价')
    return
  }
  const kwh = Number((amount / price).toFixed(2))
  try {
    const res: any = await request.post('/electricity/recharge', {
      recordDate: currentRecharge.value.record_date,
      kwh,
      price
    })
    if (res.code === 200) {
      ElMessage.success('充值信息已更新')
      rechargeDialogVisible.value = false
      fetchHistory()
    }
  } catch {
    ElMessage.error('保存失败')
  }
}

const handleResize = () => chart?.resize()

onMounted(() => {
  fetchHistory()
  fetchRealtimeBalance()
  fetchRanking('top')
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
})
</script>

<style scoped lang="scss">
.electricity-page {
  min-height: 100vh;
  background: var(--page-bg);
}

.status-bar-placeholder {
  height: 60px;
}

.detail-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.back-btn {
  flex-shrink: 0;
}

.page-title {
  margin: 0;
  font-size: 20px;
  color: var(--text-primary);
}

.room-badge {
  background: var(--accent-light);
  color: var(--accent);
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 500;
}

.collect-btn {
  margin-left: auto;
}

.loading-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 80px 0;
  color: var(--text-secondary);
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.stat-card {
  background: var(--card-bg);
  border-radius: 10px;
  padding: 16px;
  box-shadow: var(--card-shadow);
  text-align: center;
}

.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);

  .unit {
    font-size: 12px;
    font-weight: 400;
    color: var(--text-secondary);
  }

  &.personal {
    color: var(--accent);
  }

  &.building {
    color: var(--text-secondary);
  }

  &.balance {
    color: #67c23a;
  }
}

.range-switch {
  margin-bottom: 16px;
}

.chart-wrapper {
  background: var(--card-bg);
  border-radius: 12px;
  padding: 20px;
  box-shadow: var(--card-shadow);
  margin-bottom: 20px;
}

.chart-container {
  width: 100%;
  height: 350px;
}

.recharge-section {
  background: var(--card-bg);
  border-radius: 12px;
  padding: 20px;
  box-shadow: var(--card-shadow);
}

.section-title {
  margin: 0 0 16px 0;
  font-size: 16px;
  color: var(--text-primary);
}

.recharge-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recharge-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px;
  background: var(--page-bg);
  border-radius: 8px;

  &.unconfirmed {
    background: #fef0f0;
    border: 1px solid #fbc4c4;
  }
}

.recharge-date {
  font-size: 14px;
  color: var(--text-regular);
  min-width: 100px;
}

.recharge-info {
  flex: 1;
  font-size: 14px;
  color: var(--text-primary);
}

.unconfirmed-text {
  color: #f56c6c;
}

.ranking-section {
  background: var(--card-bg);
  border-radius: 12px;
  padding: 20px;
  box-shadow: var(--card-shadow);
  margin-bottom: 20px;
}

.ranking-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.ranking-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ranking-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  background: var(--page-bg);
  border-radius: 8px;
  gap: 12px;

  &.my-room {
    background: #f0f9eb;
    border: 2px solid #67c23a;
  }
}

.rank-badge {
  width: 24px;
  height: 24px;
  background: #909399;
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;

  &.rank-1 {
    background: #f56c6c;
  }

  &.rank-2 {
    background: #e6a23c;
  }

  &.rank-3 {
    background: var(--accent);
  }
}

.room-name {
  flex: 1;
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 500;
}

.consumption {
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 600;
}

.my-tag {
  font-size: 12px;
  color: #67c23a;
  font-weight: 600;
  margin-left: 4px;
}

.ranking-empty {
  text-align: center;
  color: var(--text-secondary);
  font-size: 14px;
  padding: 20px 0;
}

.recharge-form {
  .form-item {
    margin-bottom: 16px;

    label {
      display: block;
      margin-bottom: 8px;
      font-size: 14px;
      color: var(--text-regular);
    }
  }

  .calculated-amount {
    font-size: 18px;
    font-weight: 600;
    color: #e6a23c;
    padding: 8px 0;
  }
}

@media screen and (max-width: 768px) {
  .detail-container {
    padding: 12px;
  }

  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .chart-container {
    height: 280px;
  }

  .detail-header {
    flex-wrap: wrap;
  }

  .recharge-item {
    flex-wrap: wrap;
    gap: 8px;
  }

  .ranking-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
