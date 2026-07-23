<template>
  <div class="hello">
    <h1>{{ msg }}</h1>
    <p>欢迎使用记账系统</p>
  </div>
</template>

<script setup>
defineProps({
  msg: {
    type: String,
    required: true
  }
})
</script>

<style scoped>
.hello {
  text-align: center;
  margin-top: 60px;
}
</style>
