<template>
  <div class="bill-page">
    <div class="page-header">
      <div class="filter-bar">
        <el-select v-model="filter.type" placeholder="全部类型" clearable style="width: 140px">
          <el-option label="全部" :value="null" />
          <el-option label="收入" :value="1" />
          <el-option label="支出" :value="2" />
        </el-select>

        <el-select
          v-model="filter.categoryId"
          placeholder="全部分类"
          clearable
          style="width: 160px"
        >
          <el-option
            v-for="item in filteredCategoryOptions"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>

        <el-date-picker
          v-model="filter.dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 280px"
        />

        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-button type="primary" @click="handleAdd">新增记账</el-button>
    </div>

    <el-table :data="billList" border style="width: 100%">
      <el-table-column prop="billDate" label="日期" width="120" />
      <el-table-column label="类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.type === 1 ? 'success' : 'danger'">
            {{ row.type === 1 ? '收入' : '支出' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="categoryName" label="分类" width="120" />
      <el-table-column label="金额" width="120" align="right">
        <template #default="{ row }">
          <span :style="{ color: row.type === 1 ? '#67C23A' : '#F56C6C' }">
            {{ row.type === 1 ? '+' : '-' }}{{ row.amount?.toFixed(2) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" show-overflow-tooltip />
      <el-table-column label="操作" width="150px" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>

    <BillFormDialog
      v-model:visible="dialogVisible"
      :bill="currentBill"
      :categories="categoryOptions"
      @success="handleDialogSuccess"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request.js'
import BillFormDialog from '@/components/BillFormDialog.vue'

const filter = reactive({
  type: null,
  categoryId: null,
  dateRange: []
})

const categoryOptions = ref([])
const billList = ref([])
const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const dialogVisible = ref(false)
const currentBill = ref(null)

const filteredCategoryOptions = computed(() => {
  if (!filter.type) {
    return categoryOptions.value
  }
  return categoryOptions.value.filter(c => c.type === filter.type)
})

/**
 * 获取分类列表
 */
async function fetchCategories() {
  try {
    const res = await request.get('/categories')
    if (res.code === 200) {
      categoryOptions.value = res.data || []
    }
  } catch (error) {
    // 错误已由 request 拦截器处理
  }
}

function formatDate(date) {
  if (!date) return ''
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/**
 * 获取记账列表
 */
async function fetchBills() {
  try {
    const params = {
      page: pagination.page,
      size: pagination.size
    }
    if (filter.type) {
      params.type = filter.type
    }
    if (filter.categoryId) {
      params.categoryId = filter.categoryId
    }
    if (filter.dateRange && filter.dateRange.length === 2) {
      params.startDate = formatDate(filter.dateRange[0])
      params.endDate = formatDate(filter.dateRange[1])
    }
    const res = await request.get('/bills', { params })
    if (res.code === 200 && res.data) {
      const list = res.data.list || []
      list.forEach(bill => {
        const category = categoryOptions.value.find(c => c.id === bill.categoryId)
        bill.categoryName = category ? category.name : ''
      })
      billList.value = list
      pagination.total = res.data.total || 0
      pagination.page = res.data.page || 1
      pagination.size = res.data.size || 10
    }
  } catch (error) {
    // 错误已由 request 拦截器处理
  }
}

function handleSearch() {
  pagination.page = 1
  fetchBills()
}

function handleReset() {
  filter.type = null
  filter.categoryId = null
  filter.dateRange = []
  pagination.page = 1
  fetchBills()
}

function handleAdd() {
  currentBill.value = null
  dialogVisible.value = true
}

function handleEdit(row) {
  currentBill.value = { ...row }
  dialogVisible.value = true
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定要删除该条记账记录吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/bills/${row.id}`)
      ElMessage.success('删除成功')
      await fetchBills()
    } catch (error) {
      // 错误已由 request 拦截器处理
    }
  })
}

function handleDialogSuccess() {
  fetchBills()
}

function handleSizeChange(size) {
  pagination.size = size
  pagination.page = 1
  fetchBills()
}

function handlePageChange(page) {
  pagination.page = page
  fetchBills()
}

onMounted(async () => {
  await fetchCategories()
  await fetchBills()
})
</script>

<style scoped>
.bill-page {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
