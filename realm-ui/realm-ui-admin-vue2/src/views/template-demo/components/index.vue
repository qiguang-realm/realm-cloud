<template>
  <div class="app-container">
    <el-card style="margin: 5px 0">
      <el-row :gutter="20">

        <el-col class="font-input" :span="5">
          <font>企业名称：</font>
          <el-input v-model="listQuery.corporateName" class="my-class" placeholder="请输入企业名称" clearable/>
        </el-col>

      </el-row>
      <el-col class="font-button" :span="24">
        <el-button
          type="primary" plain
          @click="handleBatchAdd"
        >
          批量添加
        </el-button>
        <el-button
          type="primary"
          icon="el-icon-search"
          @click="getList"
        >搜索
        </el-button>
        <el-button
          icon="el-icon-refresh-left"
          @click="resetParam"
        >重置
        </el-button>
      </el-col>
    </el-card>
    <el-card style="margin: 2px 0;height:700px">
      <el-table
        :key="tableKey"
        ref="enterpriseTable"
        v-loading="listLoading"
        :data="list"
        border
        fit
        highlight-current-row
        stripe
        height="575"
        :cell-style="imageStyle"
        :header-cell-style="{
          fontSize: '15px',
          fontWight: 'bold',
          color: '#333',
        }"
        @selection-change="handleSelectionChange"
      >

        <el-table-column type="selection" width="55" align="center"/>

        <el-table-column label="企业名称" align="center" prop="corporateName" show-overflow-tooltip/>
        <el-table-column label="车辆数" align="center" prop="vehiclesNum"/>


        <el-table-column
          label="操作"
          align="center"
          fixed="right"
          width="160"
        >
          <template slot-scope="{ row}">

            <el-button
              size="small"
              type="success"
              plain
              @click="handleEdit(row.corporateName)"
            >配置排行榜
            </el-button>

          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="total > 0"
        align="right"
        :total="total"
        :page.sync="listQuery.pageNum"
        :limit.sync="listQuery.pageSize"
        @pagination="getList"
      />
    </el-card>
    <el-dialog
      v-if="open"
      top="15vh"
      :title="title"
      :visible.sync="open"
      width="30%"
    >
      <info-edit :enterpriseName="enterpriseName" :source="source" @fatherMethod="refresh"/>
    </el-dialog>
    <el-dialog
      v-if="editOpen"
      top="15vh"
      :title="title"
      :visible.sync="editOpen"
      width="30%"
    >
      <enterprise-edit :enterpriseNames="enterpriseNames" @fatherMethod="refresh"/>
    </el-dialog>
  </div>
</template>

<script>
import {enterpriseVehiclesPageList} from '@/api/userVehicle'
import Pagination from '@/components/Pagination'
import InfoEdit from '@/views/rankingList/enterpriseVehicles/profile/InfoEdit.vue'
import enterpriseEdit from '@/views/rankingList/enterpriseVehicles/profile/enterprise-edit.vue'

export default {
  name: 'Index',
  components: {InfoEdit, Pagination, enterpriseEdit},
  props: {
    type: {
      type: String,
      default: null
    }
  },
  data() {
    return {
      tableKey: 0,
      list: null,
      total: 0,
      listLoading: true,
      listQuery: {
        pageNum: 1,
        pageSize: 10,
        startTime: null,
        endTime: null,
        corporateName: null,
      },
      enterpriseName: null,
      open: false,
      editOpen: false,
      title: '',
      source: null,
      multipleSelection: [],
      enterpriseNames: [],

    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.listLoading = true
      enterpriseVehiclesPageList(this.listQuery).then((res) => {
        if (res.code === '0') {
          const page = res.page
          this.list = page.records
          this.total = parseInt(page.total)
        }
        setTimeout(() => {
          this.listLoading = false
        }, 200)
      }).finally(() => {
        this.$nextTick(() => {
          this.$refs.enterpriseTable.doLayout()
        })
      })
    },

    resetParam: function () {
      this.listQuery.corporateName = null
      this.getList()
    },
    handleFilter() {
      this.listQuery.page = 1
      this.getList()
    },
    imageStyle({row, column, rowIndex, columnIndex}) {
      const cellStyle = 'background:#ffff'
      if (column.label === '图片') {
        return cellStyle
      }
    },
    handleEdit(corporateName, type) {
      this.source = '02'
      if (corporateName) {
        if (type === '01') {
          this.title = '查看信息'
          this.source = type
        } else {
          this.title = '添加配置'
        }
      } else {
        this.title = '添加信息'
      }
      this.enterpriseName = corporateName
      this.open = true
    },
    refresh() {
      this.open = false
      this.editOpen = false
      this.getList()
    },
    renderHeader(h, {column}) {
      const cols = column.label.split('|')
      const ret = []
      for (let i = 0; i < cols.length; i++) {
        ret.push(h('span', {}, cols[i]))
        if (i < cols.length - 1) {
          ret.push(h('br'))
        }
      }
      return h('span', {}, ret)
    },
    handleSelectionChange(val) {
      this.multipleSelection = val
    },
    handleBatchAdd() {
      if (this.multipleSelection.length == 0) {
        this.$message.error('请先至少选择一项')
      }

      if (this.multipleSelection.length > 0) {
        this.title = '批量添加企业车辆数'
        this.editOpen = true
        this.enterpriseNames = this.$refs.enterpriseTable.selection.map(item => item.corporateName)
        // this.$refs.enterpriseTable.clearSelection()
        // console.info(this.enterpriseNames)
      }

    },

    formatJson(filterVal, jsonData) {
      return jsonData.map(v => filterVal.map(j => v[j]))
    }
  }
}
</script>
<style lang="scss" scoped>
.mb-4 {
  position: absolute;
  z-index: 1;

  el-col {
    margin-top: 10px;
    margin-right: 40px;
  }

}

.hs-flex {
  margin-left: 10px !important;
  /* margin-right: -5px; */
  background-color: #ffffffa3;
  height: 60px;
  box-shadow: 1px 1px 3px 3px #666666ab;
  margin-top: 10px;
  line-height: 60px;
  display: flex;
  align-items: center;
  justify-content: left;
  width: 70%;
  /* margin-left: 10%; */
  border-radius: 10px;
  padding-left: 15px !important;
}
</style>
