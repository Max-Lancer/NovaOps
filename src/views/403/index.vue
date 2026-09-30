<template>
  <main class="result-wrap">
    <a-result
      status="403"
      title="403"
      sub-title="抱歉，你没有权限访问该页面。"
    >
      <template #extra>
        <RouterLink to="/home">
          <a-button type="primary">返回 Dashboard</a-button>
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
