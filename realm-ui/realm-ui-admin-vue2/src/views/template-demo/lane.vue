<template>
  <div class="app-container">
    <el-card style="margin: 5px 0">
      <el-row :gutter="20">

        <el-col class="font-input" :span="7">
          <font>车道ID：</font>
          <el-input v-model="listQuery.laneId" class="my-class" placeholder="请输入车道ID" clearable/>
        </el-col>
        <el-col class="font-input" :span="5">
          <font>车牌号：</font>
          <el-input v-model="listQuery.licensePlateNumber" class="my-class" placeholder="请输入车牌号" clearable/>
        </el-col>

        <el-col class="font-input" :span="5">
          <font>出口车型：</font>
          <el-select v-model="listQuery.exitVehicleType" clearable>
            <el-option label="客一" value="客一">客一</el-option>
            <el-option label="客二" value="客二">客二</el-option>
            <el-option label="客三" value="客三">客三</el-option>
            <el-option label="客四" value="客四">客四</el-option>
            <el-option label="货一" value="货一">货一</el-option>
            <el-option label="货二" value="货二">货二</el-option>
            <el-option label="货三" value="货三">货三</el-option>
            <el-option label="货四" value="货四">货四</el-option>
            <el-option label="货五" value="货五">货五</el-option>
            <el-option label="货六" value="货六">货六</el-option>
          </el-select>
        </el-col>

        <el-col class="font-input" :span="5">
          <font>支付方式：</font>
          <el-select v-model="listQuery.payType" clearable>
            <el-option label="ETC" value="ETC">ETC</el-option>
            <el-option label="现金" value="现金">现金</el-option>
            <el-option label="银联" value="银联">银联</el-option>
            <el-option label="未缴费" value="未缴费">未缴费</el-option>
          </el-select>
        </el-col>

        <el-col class="font-input" :span="6">
          <font>开始时间：</font>
          <el-date-picker
            v-model="listQuery.startTime"
            class="filter-item"
            value-format="yyyy-MM-dd HH:mm:ss"
            type="datetime"
            placeholder="请输入开始时间"
          />
        </el-col>

        <el-col class="font-input" :span="6">
          <font>结束时间：</font>
          <el-date-picker
            v-model="listQuery.endTime"
            class="filter-item"
            value-format="yyyy-MM-dd HH:mm:ss"
            type="datetime"
            placeholder="请输入结束时间"
          />
        </el-col>

      </el-row>
      <el-row :gutter="20">
        <el-col class="font-button" :span="24">

          <el-button v-waves class="filter-item" type="primary" icon="el-icon-search" @click="handleFilter">
            检索
          </el-button>

          <el-button v-waves class="filter-item" type="info" icon="el-icon-refresh" @click="handleReset">
            重置
          </el-button>

          <el-upload
            action="#"
            :http-request="uploadFile"
            :show-file-list="false"
            multiple
            :auto-upload="true"
            accept=".txt,.csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel"
          >
            <el-button v-waves style="margin-left: 10px; padding: 12px; font-size: 11px;" size="small" type="info"
                       icon="el-icon-edit-outline">导入车道数据
            </el-button>
          </el-upload>

        </el-col>
      </el-row>

    </el-card>
    <el-card style="margin: 2px 0;height:639px">

<!--      <el-table-->
<!--        :key="tableKey"-->
<!--        v-loading="listLoading"-->
<!--        :data="list"-->
<!--        border-->
<!--        fit-->
<!--        highlight-current-row-->
<!--        style="width: 100%;"-->
<!--        @sort-change="sortChange"-->
<!--      >-->

      <el-table
        :key="tableKey"
        ref="multipleTable"
        v-loading="listLoading"
        :data="list"
        element-loading-text="Loading"
        border
        fit
        highlight-current-row
        stripe
        height="575"
        :header-cell-style="{
          fontSize: '15px',
          fontWight: 'bold',
          color: '#333',
        }"
      >

        <el-table-column label="车道ID" align="center" prop="laneId" width="350"/>

        <el-table-column label="车牌号" align="center" prop="licensePlateNumber"/>

        <el-table-column label="出口车型" align="center" prop="exitVehicleType"/>

        <el-table-column label="支付方式" align="center" prop="payType"/>

        <el-table-column label="出口时间" align="center" prop="exitTime"/>

      </el-table>

      <pagination
        v-show="total>0"
        :total="total"
        :page.sync="listQuery.pageNum"
        :limit.sync="listQuery.pageSize"
        layout="->,total, sizes, prev, pager, next, jumper"
        @pagination="fetchData"
      />

    </el-card>

  </div>
</template>

<script>

import {fetchFile, fetchList} from '@/api/basic'
import waves from '@/directive/waves' // waves directive
import Pagination from '@/components/Pagination' // secondary package based on el-pagination


export default {
  name: 'Index',
  components: {
    Pagination
  },
  directives: {
    waves
  },

  data() {
    return {
      // 用于触发表格重新渲染的键值
      tableKey: 0,
      // 列表数据
      list: null,
      // 总记录数
      total: 0,
      // 列表加载状态
      listLoading: true,
      // 查询参数
      listQuery: {
        // 当前页码
        pageNum: 1,
        // 每页显示的记录数
        pageSize: 10,

        startTime: null,
        endTime: null,
        laneId: null,
        licensePlateNumber: null,
        exitVehicleType: null,
        payType: null,

      },

      // 对话框是否可见
      dialogFormVisible: false,
      // 对话框状态（例如：'create', 'edit'）
      dialogStatus: '',
    }
  },

  methods: {
    // Methods to handle user interactions or other logic
    fetchData() {
      // Fetch data from an API or other source
      // Example: this.items = apiResponse.data;
      this.listLoading = true;
      fetchList(this.listQuery).then(response => {
        if (response.code === '0') {
          const {records, total} = response.page;
          this.list = records;
          this.total = parseInt(total);
        } else {
          this.$message({
            message: `获取列表失败`,
            type: 'error'
          });
        }
      }).catch(error => {
        this.$message({
          message: `请求失败`,
          type: 'error'
        });
      }).finally(() => {
        // Just to simulate the time of the request
        setTimeout(() => {
          this.listLoading = false;
        }, 200);
      });
    },

    // 上传文件
    uploadFile(options) {
      this.listLoading = true;
      const fileObj = options.file;

      if (!fileObj) {
        this.$message({
          message: '请上传文件',
          type: 'warning'
        });
        this.listLoading = false;
        return;
      }

      const validTypes = [
        'text/plain',
        'text/csv',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'application/vnd.ms-excel'
      ];

      if (!validTypes.includes(fileObj.type)) {
        this.$message({
          message: '格式错误，请重新上传',
          type: 'warning'
        });
        this.listLoading = false;
        return;
      }

      const file = {
        file: fileObj,
      };

      fetchFile(file).then(response => {
        if (response.code === '0') {
          this.$message.success('上传成功');
          this.getList();
        } else {
          this.$message({
            message: `上传失败，请稍后再试`,
            type: 'error'
          });
        }
      }).catch(error => {
        this.$message({
          message: `请求失败，请检查网络连接或稍后再试`,
          type: 'error'
        });
      }).finally(() => {
        // Just to simulate the time of the request
        // setTimeout(() => {
        //   this.listLoading = false;
        // }, 200);

        this.listLoading = false;
      });
    },

    handleFilter: function () {
      // 将页码重置为第一页，以确保过滤后的数据从第一页开始显示
      this.listQuery.pageNum = 1;

      // 调用 getList 方法以获取更新后的列表数据
      this.getList();
    },

    refresh: function () {
      this.getList();
    },

    handleReset: function () {
      this.resetListQuery();
      this.getList();
    },

    resetListQuery: function () {
      this.listQuery = {
        laneId: null,
        licensePlateNumber: null,
        exitVehicleType: null,
        payType: null,
        startTime: null,
        endTime: null
      };
    }

  },

  created() {
    // Lifecycle hook to fetch data when the component is created
    this.fetchData();
  },

}
</script>
<style lang="scss" scoped>

</style>
