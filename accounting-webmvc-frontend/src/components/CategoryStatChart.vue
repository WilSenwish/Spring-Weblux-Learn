<template>
  <div class="category-stat-chart">
    <v-chart :option="chartOption" style="height: 400px" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
import VChart from 'vue-echarts'

use([CanvasRenderer, PieChart, TooltipComponent, LegendComponent, TitleComponent])

const props = defineProps({
  categoryStats: {
    type: Array,
    default: () => []
  },
  type: {
    type: Number,
    default: 2
  }
})

const title = computed(() => {
  return props.type === 1 ? '收入分类占比' : '支出分类占比'
})

const chartOption = computed(() => {
  const data = props.categoryStats.map(item => ({
    name: item.categoryName,
    value: item.amount,
    percentage: item.percentage
  }))

  return {
    title: {
      text: title.value,
      left: 'center'
    },
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        const p = params.data.percentage
        return `${params.name}<br/>金额: ${params.value?.toFixed(2)}<br/>占比: ${p?.toFixed(2)}%`
      }
    },
    legend: {
      orient: 'vertical',
      right: 0,
      top: 'center'
    },
    series: [
      {
        type: 'pie',
        radius: '60%',
        center: ['40%', '50%'],
        data: data,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }
})
</script>

<style scoped>
.category-stat-chart {
  width: 100%;
}
</style>
