<template>
  <div class="category-page">
    <div class="page-header">
      <h2 class="page-title">分类管理</h2>
      <el-button type="primary" @click="handleAdd">新增分类</el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="收入分类" name="1">
        <el-table :data="incomeList" border style="width: 100%">
          <el-table-column prop="name" label="分类名称" />
          <el-table-column label="操作" width="150px" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
              <el-button
                v-if="row.isPreset !== 1"
                link
                type="danger"
                size="small"
                @click="handleDelete(row)"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="支出分类" name="2">
        <el-table :data="expenseList" border style="width: 100%">
          <el-table-column prop="name" label="分类名称" />
          <el-table-column label="操作" width="150px" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
              <el-button
                v-if="row.isPreset !== 1"
                link
                type="danger"
                size="small"
                @click="handleDelete(row)"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑分类' : '新增分类'"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="80px"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入分类名称" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :label="1">收入</el-radio>
            <el-radio :label="2">支出</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request.js'

const activeTab = ref('1')
const incomeList = ref([])
const expenseList = ref([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const form = reactive({
  id: null,
  name: '',
  type: 1
})

const rules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

/**
 * 获取分类列表
 */
async function fetchCategories() {
  try {
    const res = await request.get('/categories', { params: { type: parseInt(activeTab.value) } })
    if (res.code === 200) {
      if (activeTab.value === '1') {
        incomeList.value = res.data || []
      } else {
        expenseList.value = res.data || []
      }
    }
  } catch (error) {
    // 错误已由 request 拦截器处理
  }
}

function handleTabChange() {
  if (activeTab.value === '1' && incomeList.value.length === 0) {
    fetchCategories()
  } else if (activeTab.value === '2' && expenseList.value.length === 0) {
    fetchCategories()
  }
}

function resetForm() {
  form.id = null
  form.name = ''
  form.type = parseInt(activeTab.value)
}

function handleAdd() {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  form.id = row.id
  form.name = row.name
  form.type = row.type || parseInt(activeTab.value)
  dialogVisible.value = true
}

function handleSubmit() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (isEdit.value) {
        await request.put(`/categories/${form.id}`, {
          name: form.name,
          type: form.type
        })
        ElMessage.success('编辑成功')
      } else {
        await request.post('/categories', {
          name: form.name,
          type: form.type
        })
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      await fetchCategories()
      // 如果切换了类型标签，需要刷新另一个列表
      const formTypeStr = String(form.type)
      if (isEdit.value && formTypeStr !== activeTab.value) {
        const oldType = activeTab.value
        activeTab.value = formTypeStr
        await fetchCategories()
        activeTab.value = oldType
      }
    } catch (error) {
      // 错误已由 request 拦截器处理
    }
  })
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定要删除分类「${row.name}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/categories/${row.id}`)
      ElMessage.success('删除成功')
      await fetchCategories()
    } catch (error) {
      // 错误已由 request 拦截器处理
    }
  })
}

onMounted(() => {
  fetchCategories()
})
</script>

<style scoped>
.category-page {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: bold;
}
</style>
