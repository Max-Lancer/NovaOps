<template>
  <main class="result-wrap">
    <a-result
      status="404"
      title="404"
      sub-title="页面不存在。"
    >
      <template #extra>
        <RouterLink to="/home">
          <a-button type="primary">返回工作台</a-button>
        </RouterLink>
      </template>
    </a-result>
  </main>
</template>

<style scoped>
.result-wrap {
  display: grid;
  place-items: center;
  min-height: 100vh;
}
</style>
