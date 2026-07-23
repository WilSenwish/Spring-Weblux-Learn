<template>
  <div class="period-stat-chart">
    <el-row :gutter="20" class="summary-row">
      <el-col :span="8">
        <el-card>
          <div class="summary-item">
            <div class="summary-label">总收入</div>
            <div class="summary-value" style="color: #67C23A">{{ totalIncome?.toFixed(2) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <div class="summary-item">
            <div class="summary-label">总支出</div>
            <div class="summary-value" style="color: #F56C6C">{{ totalExpense?.toFixed(2) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <div class="summary-item">
            <div class="summary-label">结余</div>
            <div class="summary-value" :style="{ color: balance >= 0 ? '#67C23A' : '#F56C6C' }">
              {{ balance?.toFixed(2) }}
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <v-chart :option="chartOption" style="height: 400px; margin-top: 20px" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
import VChart from 'vue-echarts'

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent])

const props = defineProps({
  title: {
    type: String,
    default: ''
  },
  periodStats: {
    type: Array,
    default: () => []
  },
  xAxisName: {
    type: String,
    default: '日期'
  },
  totalIncome: {
    type: Number,
    default: 0
  },
  totalExpense: {
    type: Number,
    default: 0
  },
  balance: {
    type: Number,
    default: 0
  }
})

const chartOption = computed(() => {
  const xData = props.periodStats.map(item => item.period)
  const incomeData = props.periodStats.map(item => item.income || 0)
  const expenseData = props.periodStats.map(item => item.expense || 0)
  const balanceData = props.periodStats.map(item => (item.income || 0) - (item.expense || 0))

  return {
    title: {
      text: props.title,
      left: 'center'
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    legend: {
      data: ['收入', '支出', '结余'],
      bottom: 0
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      name: props.xAxisName,
      data: xData,
      axisLabel: {
        rotate: xData.length > 10 ? 45 : 0
      }
    },
    yAxis: {
      type: 'value',
      name: '金额'
    },
    series: [
      {
        name: '收入',
        type: 'bar',
        data: incomeData,
        itemStyle: {
          color: '#67C23A'
        }
      },
      {
        name: '支出',
        type: 'bar',
        data: expenseData,
        itemStyle: {
          color: '#F56C6C'
        }
      },
      {
        name: '结余',
        type: 'line',
        data: balanceData,
        itemStyle: {
          color: '#409EFF'
        },
        lineStyle: {
          width: 3
        }
      }
    ]
  }
})
</script>

<style scoped>
.summary-row {
  margin-bottom: 0;
}
.summary-item {
  text-align: center;
}
.summary-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}
.summary-value {
  font-size: 24px;
  font-weight: bold;
}
</style>
