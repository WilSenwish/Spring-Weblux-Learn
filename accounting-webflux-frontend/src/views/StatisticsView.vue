<template>
  <div class="statistics-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="按周统计" name="weekly">
        <PeriodStatChart
          v-if="weeklyData"
          title="本周收支统计"
          :period-stats="weeklyData.periodStats"
          x-axis-name="日期"
          :total-income="weeklyData.totalIncome"
          :total-expense="weeklyData.totalExpense"
          :balance="weeklyData.balance"
        />
      </el-tab-pane>

      <el-tab-pane label="按月统计" name="monthly">
        <div class="filter-bar">
          <el-date-picker
            v-model="monthlyDate"
            type="month"
            placeholder="选择月份"
            @change="fetchMonthlyData"
          />
        </div>
        <PeriodStatChart
          v-if="monthlyData"
          title="当月收支统计"
          :period-stats="monthlyData.periodStats"
          x-axis-name="日期"
          :total-income="monthlyData.totalIncome"
          :total-expense="monthlyData.totalExpense"
          :balance="monthlyData.balance"
        />
      </el-tab-pane>

      <el-tab-pane label="按年统计" name="yearly">
        <div class="filter-bar">
          <el-select v-model="yearlyYear" placeholder="选择年份" @change="fetchYearlyData">
            <el-option v-for="year in yearOptions" :key="year" :label="year + '年'" :value="year" />
          </el-select>
        </div>
        <PeriodStatChart
          v-if="yearlyData"
          title="当年收支统计"
          :period-stats="yearlyData.periodStats"
          x-axis-name="月份"
          :total-income="yearlyData.totalIncome"
          :total-expense="yearlyData.totalExpense"
          :balance="yearlyData.balance"
        />
      </el-tab-pane>

      <el-tab-pane label="按分类统计" name="category">
        <div class="filter-bar">
          <el-radio-group v-model="categoryFilter.type">
            <el-radio-button :label="''">全部</el-radio-button>
            <el-radio-button :label="1">收入</el-radio-button>
            <el-radio-button :label="2">支出</el-radio-button>
          </el-radio-group>
          <el-date-picker
            v-model="categoryFilter.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
          <el-button type="primary" @click="fetchCategoryData">查询</el-button>
        </div>

        <div v-if="categoryData" class="category-content">
          <el-row :gutter="20">
            <el-col v-if="showIncomeChart" :span="showExpenseChart ? 12 : 24">
              <CategoryStatChart
                :category-stats="incomeCategoryStats"
                :type="1"
              />
            </el-col>
            <el-col v-if="showExpenseChart" :span="showIncomeChart ? 12 : 24">
              <CategoryStatChart
                :category-stats="expenseCategoryStats"
                :type="2"
              />
            </el-col>
          </el-row>

          <el-table :data="categoryData.categoryStats" border style="width: 100%; margin-top: 20px">
            <el-table-column prop="categoryName" label="分类名称" />
            <el-table-column label="类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.type === 1 ? 'success' : 'danger'">
                  {{ row.type === 1 ? '收入' : '支出' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="金额" align="right">
              <template #default="{ row }">
                {{ row.amount?.toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column label="占比" align="right">
              <template #default="{ row }">
                {{ row.percentage?.toFixed(2) }}%
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '@/utils/request.js'
import PeriodStatChart from '@/components/PeriodStatChart.vue'
import CategoryStatChart from '@/components/CategoryStatChart.vue'

const activeTab = ref('weekly')

const weeklyData = ref(null)
const monthlyData = ref(null)
const yearlyData = ref(null)
const categoryData = ref(null)

const monthlyDate = ref(new Date())
const yearlyYear = ref(new Date().getFullYear())
const yearOptions = Array.from({ length: 5 }, (_, i) => new Date().getFullYear() - 2 + i)

const categoryFilter = ref({
  type: '',
  dateRange: [new Date(new Date().getFullYear(), new Date().getMonth(), 1), new Date()]
})

const incomeCategoryStats = computed(() => {
  return categoryData.value?.categoryStats?.filter(item => item.type === 1) || []
})

const expenseCategoryStats = computed(() => {
  return categoryData.value?.categoryStats?.filter(item => item.type === 2) || []
})

const showIncomeChart = computed(() => {
  return categoryFilter.value.type === '' || categoryFilter.value.type === 1
})

const showExpenseChart = computed(() => {
  return categoryFilter.value.type === '' || categoryFilter.value.type === 2
})

/**
 * 格式化日期为 yyyy-MM-dd
 */
function formatDate(date) {
  if (!date) return ''
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/**
 * 获取本周统计数据
 */
async function fetchWeeklyData() {
  try {
    const res = await request.get('/statistics/weekly')
    if (res.code === 200) {
      weeklyData.value = res.data
    }
  } catch (error) {
    // 错误已由 request 拦截器处理
  }
}

/**
 * 获取当月统计数据
 */
async function fetchMonthlyData() {
  if (!monthlyDate.value) return
  const d = new Date(monthlyDate.value)
  const year = d.getFullYear()
  const month = d.getMonth() + 1
  try {
    const res = await request.get('/statistics/monthly', { params: { year, month } })
    if (res.code === 200) {
      monthlyData.value = res.data
    }
  } catch (error) {
    // 错误已由 request 拦截器处理
  }
}

/**
 * 获取当年统计数据
 */
async function fetchYearlyData() {
  try {
    const res = await request.get('/statistics/yearly', { params: { year: yearlyYear.value } })
    if (res.code === 200) {
      yearlyData.value = res.data
    }
  } catch (error) {
    // 错误已由 request 拦截器处理
  }
}

/**
 * 获取分类统计数据
 */
async function fetchCategoryData() {
  const params = {}
  if (categoryFilter.value.type !== '') {
    params.type = categoryFilter.value.type
  }
  if (categoryFilter.value.dateRange && categoryFilter.value.dateRange.length === 2) {
    params.startDate = formatDate(categoryFilter.value.dateRange[0])
    params.endDate = formatDate(categoryFilter.value.dateRange[1])
  }
  try {
    const res = await request.get('/statistics/category', { params })
    if (res.code === 200) {
      categoryData.value = res.data
    }
  } catch (error) {
    // 错误已由 request 拦截器处理
  }
}

/**
 * 切换标签页时加载对应数据
 */
function handleTabChange(tab) {
  if (tab === 'weekly' && !weeklyData.value) {
    fetchWeeklyData()
  } else if (tab === 'monthly' && !monthlyData.value) {
    fetchMonthlyData()
  } else if (tab === 'yearly' && !yearlyData.value) {
    fetchYearlyData()
  } else if (tab === 'category' && !categoryData.value) {
    fetchCategoryData()
  }
}

onMounted(() => {
  fetchWeeklyData()
})
</script>

<style scoped>
.statistics-page {
  padding: 20px;
}
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.category-content {
  margin-top: 10px;
}
</style>
