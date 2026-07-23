<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑记账' : '新增记账'"
    width="500px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="80px"
    >
      <el-form-item label="类型" prop="type">
        <el-radio-group v-model="form.type" @change="handleTypeChange">
          <el-radio :label="1">收入</el-radio>
          <el-radio :label="2">支出</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="分类" prop="categoryId">
        <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
          <el-option
            v-for="item in filteredCategories"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="金额" prop="amount">
        <el-input-number
          v-model="form.amount"
          :precision="2"
          :min="0.01"
          style="width: 100%"
          placeholder="请输入金额"
        />
      </el-form-item>

      <el-form-item label="日期" prop="date">
        <el-date-picker
          v-model="form.date"
          type="date"
          placeholder="请选择日期"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="3"
          placeholder="请输入备注"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request.js'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  bill: {
    type: Object,
    default: null
  },
  categories: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:visible', 'success'])

const formRef = ref(null)
const form = reactive({
  id: null,
  type: 2,
  categoryId: null,
  amount: undefined,
  date: null,
  remark: ''
})

const rules = {
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  amount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { type: 'number', min: 0.01, message: '金额必须大于0', trigger: 'blur' }
  ],
  date: [{ required: true, message: '请选择日期', trigger: 'change' }]
}

const isEdit = computed(() => !!props.bill)

const filteredCategories = computed(() => {
  return props.categories.filter(c => c.type === form.type)
})

function handleTypeChange() {
  form.categoryId = null
}

function resetForm() {
  form.id = null
  form.type = 2
  form.categoryId = null
  form.amount = undefined
  form.date = null
  form.remark = ''
}

function toDate(val) {
  if (!val) return null
  if (val instanceof Date) return val
  return new Date(val)
}

function initFormFromBill() {
  if (props.bill) {
    form.id = props.bill.id
    form.type = props.bill.type || 2
    form.categoryId = props.bill.categoryId
    form.amount = props.bill.amount
    form.date = toDate(props.bill.billDate)
    form.remark = props.bill.remark || ''
  } else {
    resetForm()
  }
}

watch(() => props.visible, (val) => {
  if (val) {
    initFormFromBill()
  }
})

watch(() => props.bill, () => {
  if (props.visible) {
    initFormFromBill()
  }
})

function formatDate(date) {
  if (!date) return ''
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function handleSubmit() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      const payload = {
        type: form.type,
        categoryId: form.categoryId,
        amount: form.amount,
        billDate: formatDate(form.date),
        remark: form.remark
      }
      if (isEdit.value) {
        await request.put(`/bills/${form.id}`, payload)
        ElMessage.success('编辑成功')
      } else {
        await request.post('/bills', payload)
        ElMessage.success('新增成功')
      }
      emit('success')
      emit('update:visible', false)
    } catch (error) {
      // 错误已由 request 拦截器处理
    }
  })
}
</script>
