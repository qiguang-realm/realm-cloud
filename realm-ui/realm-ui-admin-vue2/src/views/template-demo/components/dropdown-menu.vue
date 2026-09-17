<template>
  <div>
    <el-table :data="tableData" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55"></el-table-column>
      <el-table-column prop="date" label="日期" width="180"></el-table-column>
      <el-table-column prop="name" label="姓名" width="180"></el-table-column>
      <el-table-column label="操作">
        <template slot-scope="{ row, $index }">
          <!-- Dropdown with text description -->
          <el-dropdown @command="handleCommand($event, row, $index)">
            <!-- Button with text description -->
            <el-button type="primary" size="mini">
              更多操作 <i class="el-icon-arrow-down el-icon--right"></i>
            </el-button>
            <i class="el-icon-arrow-down el-icon--right"></i>
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item command="edit">编辑</el-dropdown-item>
              <el-dropdown-item command="delete">删除</el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
export default {
  data() {
    return {
      tableData: [
        { date: '2016-05-02', name: '王小虎' },
        { date: '2016-05-04', name: '王小猫' },
        { date: '2016-05-01', name: '王小狗' },
        { date: '2016-05-03', name: '王小兔' }
      ]
    }
  },
  methods: {
    handleSelectionChange(val) {
      console.log('选中的数据：', val)
    },
    handleCommand(command, row, index) {
      if (command === 'edit') {
        this.handleEdit(row, index)
      } else if (command === 'delete') {
        this.handleDelete(row, index)
      }
    },
    handleEdit(row, index) {
      console.log('编辑操作', row, index)
    },
    handleDelete(row, index) {
      console.log('删除操作', row, index)
    }
  }
}
</script>


<style scoped>

</style>
