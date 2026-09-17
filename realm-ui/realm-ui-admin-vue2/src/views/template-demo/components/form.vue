<template>
<!--  <el-form ref="dataForm" :rules="rules" :model="temp" label-position="left" label-width="70px" style="width: 400px; margin-left:50px;">-->
  <el-form ref="form" :model="form" label-width="80px">
    <el-form-item label="用户名">
      <el-input v-model="form.username"></el-input>
    </el-form-item>
    <el-form-item label="密码">
      <el-input type="password" v-model="form.password"></el-input>
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="submitForm('form')">提交</el-button>
    </el-form-item>
  </el-form>
</template>


<script>
import {fetchList, updateArticle} from "@/api/article";

export default {
  name: "form",
  data() {
    return {
      tableKey: 0,
      list: null,
      total: 0,
      listLoading: true,
      listQuery: {
        pageNum: 1,
        pageSize: 10,
      },
      form: {
        username: '',
        password: ''
      }
    };
  },
  methods: {
    getList() {
      this.listLoading = true
      fetchList(this.listQuery).then(response => {
        this.list = response.data.items
        this.total = response.data.total

        // Just to simulate the time of the request
        setTimeout(() => {
          this.listLoading = false
        }, 1.5 * 1000)
      })
    },
    handleFilter() {
      this.listQuery.pageNum = 1
      this.getList()
    },
    submitForm(formName) {
      this.$refs[formName].validate((valid) => {
        if (valid) {
          // const tempData = Object.assign({}, this.temp)
          // updateArticle(tempData).then(response => {
          updateArticle(this.temp)
            .then(response => {
              // 表单验证通过，可以在这里发送 AJAX 请求到服务器
              // this.$http.post('/api/submit', this.form).then(response => {
              console.log('服务器响应：', response);
              // 处理服务器响应，例如显示成功消息或跳转到其他页面
              if (response.code === '0') {
                this.$emit('fatherMethod')
              }
            })
            .catch(error => {
              console.error('请求失败：', error);
              // 处理请求失败的情况，例如显示错误消息
            });
        } else {
          console.error('表单验证失败');
          return false;
        }
      });
    },

  }

}
</script>

<style scoped>

</style>
