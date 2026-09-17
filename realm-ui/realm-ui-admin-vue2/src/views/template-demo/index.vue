<template>
  <div class="app-container">
    <el-card style="margin: 5px 0">
      <el-row :gutter="20">

        <el-col class="font-input" :span="5">
          <span style="width: 120px;">用户名称：</span>
          <el-input v-model="listQuery.userName" class="filter-item" placeholder="请输入用户名称" clearable/>
        </el-col>
        <el-col class="font-input" :span="5">
          <span style="width: 120px;">卡券券码：</span>
          <el-input v-model="listQuery.code" class="filter-item" placeholder="请输入卡券券码" clearable/>
        </el-col>

        <el-col class="font-input" :span="6">
          <span style="width: 120px;">卡券金额：</span>
          <el-input v-model="listQuery.amount" class="filter-item" placeholder="请输入卡券金额" clearable/>
        </el-col>

        <el-col class="font-input" :span="6">
          <span style="width: 120px;">订单ID：</span>
          <el-input v-model="listQuery.orderId" class="filter-item" placeholder="请输入订单ID" clearable/>
        </el-col>

        <el-col class="font-input" :span="5">
          <span style="width: 120px;">卡券状态：</span>
          <el-select v-model="listQuery.status" clearable class="filter-item">
            <el-option label="未激活" value="inactive">未激活</el-option>
            <el-option label="已兑换" value="unused">已兑换</el-option>
            <el-option label="已使用" value="used">已使用</el-option>
          </el-select>
        </el-col>

        <el-col class="font-input" :span="5">
          <span style="width: 120px;">发放状态：</span>
          <el-select v-model="listQuery.couponStatus" clearable class="filter-item">
            <el-option label="未使用" value="01">未使用</el-option>
            <el-option label="已发放" value="02">已发放</el-option>
            <el-option label="已使用" value="03">已使用</el-option>
          </el-select>
        </el-col>

        <el-col class="font-input" :span="4">
          <font>车辆类型：</font>
          <el-select v-model="listQuery.vehicleType" placeholder="请选择车辆类型" class="el-input" clearable>
            <el-option
              v-for="item in options"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-col>

        <el-col class="font-input" :span="6">
          <span style="width: 120px;">开始时间：</span>
          <el-date-picker
            v-model="listQuery.startTime"
            class="filter-item"
            value-format="yyyy-MM-dd HH:mm:ss"
            type="datetime"
            placeholder="请输入开始时间"
          />
        </el-col>
        <el-col class="font-input" :span="6">
          <span style="width: 120px;">结束时间：</span>
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
          <el-button
            class="filter-item"
            style="margin-right: auto;width:150px;"
            type="primary"
            icon="el-icon-edit"
            @click="handleCreate"
          >
            添加信息
          </el-button>

          <el-button v-waves class="filter-item" type="primary" icon="el-icon-search" @click="handleFilter">
            检索
          </el-button>

          <el-button v-waves class="filter-item" type="info" icon="el-icon-refresh" @click="handleReset">
            重置
          </el-button>

          <el-button v-waves class="filter-item" size="small" type="success" icon="el-icon-download"
                     @click="handleDownload()">
            模板下载
          </el-button>

          <el-button v-waves size="small" class="filter-item" type="primary" icon="el-icon-document"
                     @click="handleExport">
            Excel导出
          </el-button>

          <el-upload
            action="#"
            :http-request="uploadFile"
            :show-file-list="false"
            multiple
            :auto-upload="true"
            accept="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel"
          >
            <el-button v-waves style="margin-left: 10px; padding: 12px;" size="small" type="info"
                       icon="el-icon-edit-outline">Excel导入
            </el-button>
          </el-upload>

        </el-col>
      </el-row>

    </el-card>
    <el-card style="margin: 2px 0;height:700px">
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
        <el-table-column
          label="景区名称"
          prop="scenicSpotName"
          align="center"
        >
          <template slot-scope="{row}">
            <el-tag style="cursor: pointer;width:150px;">
              <span> {{ row.scenicSpotName }}</span>
            </el-tag>
          </template>

          <el-table-column
            label="检测站名称"
            prop="merchantName"
            align="center"
            width="280"
          >
            <template slot-scope="{row}">
              <span style="color: #1890ff;"> {{ row.merchantName }}</span>
            </template>
          </el-table-column>

          <!--          <template slot-scope="scope">-->
          <!--            <el-popover trigger="hover" placement="top">-->
          <!--              <p>姓名: {{ scope.row.parkingLotName }}</p>-->
          <!--              <div slot="reference" class="name-wrapper">-->
          <!--                <el-tag size="medium">{{ scope.row.parkingLotName }}</el-tag>-->
          <!--              </div>-->
          <!--            </el-popover>-->
          <!--          </template>-->

        </el-table-column>

        <el-table-column label="景区地址" align="center" prop="scenicSpotAddress" show-overflow-tooltip/>
        <el-table-column label="景区经度" align="center" prop="longitude"/>
        <el-table-column label="景区纬度" align="center" prop="latitude"/>

        <el-table-column
          label="是否旅行图鉴"
          align="center"
          prop="illustratedHandbook"
          width="120"
          v-if="dataSource !== '01' "
        >
          <template slot-scope="{row}">
            <el-tag :type="row.illustratedHandbook === '01' ? 'success' :'danger'" v-if="row.module === 3">
              <span @click="handleEditVehicleType(row)> {{ illustratedHandbookFormatter(row) }}</span>
            </el-tag>
          </template>
        </el-table-column>


        <el-table-column
          label="规则类型"
          align="center"
          prop="type"
          width="150"
        >
          <template slot-scope="{row}">
            <el-tag
              :type="row.type === '01' ? 'info' :row.type === '02' ? 'primary':row.type === '03' ? 'success':'danger'" v-if="typeFormatter(row) !== null">
              <span> {{ typeFormatter(row) }}</span>
            </el-tag>
          </template>
        </el-table-column>


        <el-table-column
          label="景区图片"
          align="center"
          prop="scenicSpotImg"
        >
          <template slot-scope="{row}">
            <img :src="row.scenicSpotImg" style="width: 30px;height: 30px">
          </template>
        </el-table-column>

        <el-table-column
          label="图片"
          align="center"
          prop="merchantImage"
          width="120"
        >
          <template slot-scope="{ row }">
            <el-image
              v-if="row.merchantImage"
              style="width: 30px; height: 30px"
              :src="row.merchantImage"
              :preview-src-list="[row.merchantImage]"
              :fit="fit"></el-image>
          </template>
        </el-table-column>

        <el-table-column
          label="行驶证背面"
          align="center"
          prop="drivingLicenseBackUrl"
          width="100"
        >
          <template scope="{row}">
            <el-image
              v-if="row.drivingLicenseBackUrl"
              style="width: 30px;height: 30px"
              :src="row.drivingLicenseBackUrl"
              fit="fit"
              :preview-src-list="[row.drivingLicenseBackUrl]"
            />
          </template>
        </el-table-column>

        <el-table-column
          prop="amount"
          label="卡券金额（元）"
          width="120"
          align="center"
        >
          <template scope="scope">
            {{ scope.row.amount | filterMoney }}
          </template>
        </el-table-column>

        <el-table-column
          prop="cardPassword"
          label="亲情卡密"
          width="220"
          align="center"
        >
          <template scope="scope">
            {{ scope.row.cardPassword | filterPassword }}
          </template>
        </el-table-column>

        <el-table-column
          label="电话"
          align="center"
          prop="mobile"
        >
          <template slot-scope="{row}">
            <span v-if="row.mobile && row.mobile.trim() !== ''">{{ row.mobile.slice(0, 3) + '****' + row.mobile.slice(row.mobile.length - 4, row.mobile.length)}}</span>
          </template>
        </el-table-column>


        <el-table-column class-name="status-col" label="Status" width="110" align="center">
          <template slot-scope="scope">
            <el-tag :type="scope.row.status | statusFilter">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>


        <el-table-column class-name="status-col" label="Status" width="110">
          <template slot-scope="{row}">
            <el-tag :type="row.status | statusFilter">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column class-name="status-col" label="Status" width="110" prop="orderStatus" align="center" >
          <template slot-scope="{row}">
            <span v-if="row.orderStatus !== null && row.orderStatus !== ''" :style="{ color: getColor(row.orderStatus) }">{{ statusText[row.orderStatus] }}</span>
          </template>
        </el-table-column>

        <!--        <el-table-column-->
        <!--          label="行驶证正面"-->
        <!--          align="center"-->
        <!--          prop="drivingLicenseFrontUrl"-->
        <!--          show-overflow-tooltip-->
        <!--        >-->
        <!--          <template slot-scope="scope">-->
        <!--            <el-popover-->
        <!--              v-if="-->
        <!--                scope.row.drivingLicenseFrontUrl !== '' &&-->
        <!--                  scope.row.drivingLicenseFrontUrl !== undefined-->
        <!--              "-->
        <!--              placement="top-start"-->
        <!--              title=""-->
        <!--              trigger="hover"-->
        <!--            >-->
        <!--              <img-->
        <!--                :src="scope.row.drivingLicenseFrontUrl"-->
        <!--                style="width: 300px;height: 300px"-->
        <!--              >-->
        <!--              <img-->
        <!--                slot="reference"-->
        <!--                :src="scope.row.drivingLicenseFrontUrl"-->
        <!--                style="width: 30px; height: 30px"-->
        <!--              >-->
        <!--            </el-popover>-->
        <!--          </template>-->
        <!--        </el-table-column>-->

        <!--        <el-table-column-->
        <!--          label="行驶证背面"-->
        <!--          align="center"-->
        <!--        >-->
        <!--          <template slot-scope="{ row }">-->
        <!--            <el-image-->
        <!--              :src="row.drivingLicenseBackUrl"-->
        <!--              style="width: 30px;height: 30px"-->
        <!--              :preview-src-list="[row.drivingLicenseBackUrl]"-->
        <!--            />-->
        <!--          </template>-->
        <!--        </el-table-column>-->

        <el-table-column label="创建时间" align="center" prop="createTime" width="160"/>

        <el-table-column label="操作" align="center" width="230" class-name="small-padding fixed-width">
          <template slot-scope="{row,$index}">

            <el-button type="primary" size="mini" @click="handleUpdate(row)">
              编辑
            </el-button>

            <el-button v-if="row.status!='deleted'" size="mini" type="danger" @click="handleDelete(row,$index)">
              删除
            </el-button>

            <el-button
              v-if="row.illustratedHandbook === '02'"
              size="mini"
              type="success"
              @click.native="handleJoin(row)"
            >
              加入图鉴
            </el-button>
            <el-button
              v-if="row.illustratedHandbook === '01'"
              size="mini"
              type="warning"
              @click.native="handleExit(row)"
            >
              退出图鉴
            </el-button>

            <el-button  v-if="row.auditStatus === '01'" size="small" type="primary" @click="handleAudit(row)">
              审核
            </el-button>

            <el-button
              size="mini"
              type="warning"
              plain
              @click="handleUpdatePrizeStatus(row)"
            > {{ row.prizeStatus==='01'?'禁用':'启用' }}
            </el-button>

            <!--            <el-button v-if="row.illustratedHandbook === '02' && row.scenicType === '01'" size="mini" type="success"-->
            <!--                       @click.native="handleJoin(row)">-->
            <!--              加入图鉴-->
            <!--            </el-button>-->
            <!--            <el-button v-if="row.illustratedHandbook === '01' && row.scenicType === '01'" size="mini" type="warning"-->
            <!--                       @click.native="handleExit(row)">-->
            <!--              退出图鉴-->
            <!--            </el-button>-->

          </template>

        </el-table-column>

        <!--        <el-table-column label="Actions" align="center" width="230" class-name="small-padding fixed-width">-->
        <!--          <template slot-scope="{row,$index}">-->
        <!--            <el-button type="primary" size="mini" @click="handleUpdate(row)">-->
        <!--              Edit-->
        <!--            </el-button>-->
        <!--            <el-button v-if="row.status!='published'" size="mini" type="success" @click="handleModifyStatus(row,'published')">-->
        <!--              Publish-->
        <!--            </el-button>-->
        <!--            <el-button v-if="row.status!='draft'" size="mini" @click="handleModifyStatus(row,'draft')">-->
        <!--              Draft-->
        <!--            </el-button>-->
        <!--            <el-button v-if="row.status!='deleted'" size="mini" type="danger" @click="handleDelete(row,$index)">-->
        <!--              Delete-->
        <!--            </el-button>-->
        <!--          </template>-->
        <!--        </el-table-column>-->

      </el-table>

      <!--      <pagination v-show="total>0" :total="total" :page.sync="listQuery.page" :limit.sync="listQuery.limit" @pagination="getList" />-->
      <pagination
        v-show="total>0"
        :total="total"
        :page.sync="listQuery.pageNum"
        :limit.sync="listQuery.pageSize"
        layout="->,total, sizes, prev, pager, next, jumper"
        @pagination="getList"
      />

    </el-card>

    <!--添加或编辑-->
    <el-dialog
      v-if="dialogFormVisible"
      top="15vh"
      :title="title"
      :visible.sync="dialogFormVisible"
      width="30%"
      :close-on-click-modal="false"
    >
      <create-edit :id="id" :dialog-status="dialogStatus" @fatherMethod="refresh"/>
    </el-dialog>

    <!--审核-->
    <el-dialog
      v-if="dialogAuditVisible"
      top="15vh"
      :title="title"
      :visible.sync="dialogAuditVisible"
      width="50%"
      :close-on-click-modal="false"
    >
      <audit :id="id" :dialogStatus="dialogStatus" @fatherMethod="refresh"/>
    </el-dialog>

  </div>
</template>

<script>

import {
  deleteScenicSpot,
  downloadExcel,
  exportExcel,
  fetchExit,
  fetchFile,
  fetchJoin,
  fetchList
} from '@/api/scenicSpot'
import waves from '@/directive/waves' // waves directive
import Pagination from '@/components/Pagination' // secondary package based on el-pagination
import Create from '@/views/roadCorrection/components/Create.vue'
import Audit from '@/views/roadCorrection/components/audit.vue'

export default {
  name: 'Index',
  components: {Pagination, CreateEdit, Audit},
  directives: {waves},

  filters: {

    // 金额分转元保留两位小数
    filterMoney (number) {
      return isNaN(number) ? 0.00 : parseFloat((number/100).toFixed(2));
    },

    // vue 实现身份证号码中间用星号隐藏  /^(.{6})(?:\w+)(.{4})$/
    filterPassword(val){
      if (val === '' || val === null) {
        return val
      }
      let reg = /^(.{4})(?:\w+)(.{4})$/;
      return val.replace(reg,'$1********$2')
    },

    // 经纬度保留小数点后四位
    filterLongitude (value) {
      if (!value) return ''
      return Number(value).toFixed(4)
      // return parseFloat(value).toFixed(2);
    },


    // 状态筛选器
    statusFilter(status) {
      const statusMap = {
        published: 'success',
        draft: 'info',
        deleted: 'danger'
      }
      return statusMap[status]
    }

  //   function Fen2Yuan( num ) {
  // if ( typeof num !== "number" || isNaN( num ) ) return null;
  // return ( num / 100 ).toFixed( 2 );
}

  },

  // props: {
  //   obj: {
  //     type: Object,
  //     default: function () {
  //       return {
  //         obje: ''
  //       }
  //     }
  //   },
  //   arr: {
  //     type: Array,
  //     default: function () {
  //       return []
  //     }
  //   }
  // },

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
        scenicSpotName: null
      },
      dialogFormVisible: false,
      dialogAuditVisible: false,
      dialogStatus: '',
      // textMap: {
      //   update: 'Edit',
      //   create: 'Create'
      // },
      title: null,
      id: null,
      dataSource:'01', //数据来源
      options :[
        {
          value: '1',
          label: '客车一类',
        },
        {
          value: '2',
          label: '客车二类',
        },
        {
          value: '3',
          label: '客车三类',
        },
        {
          value: '4',
          label: '客车四类',
        },
        {
          value: '11',
          label: '货车一类',
        },
        {
          value: '12',
          label: '货车二类',
        },
        {
          value: '13',
          label: '货车三类',
        },
        {
          value: '14',
          label: '货车四类',
        },
        {
          value: '15',
          label: '货车五类',
        },
        {
          value: '16',
          label: '货车六类',
        },
        {
          value: '21',
          label: '一型专项作业车',
        },
        {
          value: '22',
          label: '二型专项作业车',
        },
        {
          value: '23',
          label: '三型专项作业车',
        },
        {
          value: '24',
          label: '四型专项作业车',
        },
        {
          value: '25',
          label: '五型专项作业车',
        },
        {
          value: '26',
          label: '六型专项作业车',
        },
      ],
      statusText: {
        '01': '货车',
        '02': '客车',
        '03': '货车/客车'
      },
    }
  },

  created() {
    this.getList()
  },

  methods: {
    getList() {
      this.listLoading = true
      fetchList(this.listQuery).then(response => {
        if (response.code === '0') {
          const page = response.page
          this.list = page.records
          this.total = parseInt(page.total)
        }

        // this.list = response.data.items
        // this.total = response.data.total

        // Just to simulate the time of the request
        setTimeout(() => {
          this.listLoading = false
        }, 500)
      })
    },

    handleCreate() {
      this.dialogStatus = 'create'
      this.dialogFormVisible = true
      this.title = '添加信息'
      this.id = null
    },

    handleUpdate(row) {
      // this.dialogStatus = 'detail'
      this.dialogStatus = 'update'
      this.dialogFormVisible = true
      this.title = '修改信息'
      this.id = row.id
    },

    handleAudit(row) {
      // this.dialogStatus = 'detail'
      this.dialogStatus = 'audit'
      this.dialogAuditVisible = true
      this.title = '纠错审核'
      this.id = row.id
    },

    handleDelete(row, index) {
      this.$confirm('此操作将永久删除该文件, 是否继续?', '警告', {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消'
      }).then(({value}) => {
        deleteScenicSpot({id: row.id}).then(res => {
          if (res.code === '0') {
            this.$message({
              message: 'Delete Successfully',
              type: 'success'
            })
            this.handleReset()
          }
        })
      })
    },

    handleUpdatePrizeStatus(row) {
      let status = row.prizeStatus
      let text = ''
      if (status === '01') {
        status = '02'
        text = '禁用'
      } else {
        status = '01'
        text = '启用'
      }
      updatePrizeStatus({ id: row.id, prizeStatus: status }).then(res => {
        if (res.code === '0') {
          this.$message({
            type: 'success',
            message: text + '成功'
          })
          this.handleReset()
        }
      })
    },

    handleEditVehicleType(row) {
      this.dialogStatus = 'vehicle_type'
      this.dialogVehicleTypeVisible = true
      this.title = '修改车型'
      this.vehicleId = row.vehicleId
      // console.log(this.vehicleId)
    },

    getColor(status) {
      switch (status) {
        case '01':
          return 'red';
        case '02':
          return 'green';
        case '03':
          return 'blue';
        default:
          return '';
      }
    },

    handleDownload() {
      downloadExcel()
    },

    // 导出
    handleExport() {
      exportExcel(this.listQuery)
    },

    // 附件上传/导入excel
    uploadFile(param) {
      const fileObj = param.file
      if (fileObj) {
        if ((fileObj.type === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet') || (fileObj.type === 'application/vnd.ms-excel')) {
          const file = {
            file: fileObj
          }
          fetchFile(file).then(response => {
            if (response.code === '0') {
              this.$message.success('导入成功')
              this.getList()
            }
          })
        } else {
          this.$message({
            message: '附件格式错误，请删除后重新上传!',
            type: 'warning'
          })
        }
      } else {
        this.$message({
          message: '请上传附件!',
          type: 'warning'
        })
      }
    },

    // handleDelete({$index, row}) {
    //   this.$confirm('此操作将永久删除该文件, 是否继续?', '警告', {
    //     confirmButtonText: 'Confirm',
    //     cancelButtonText: 'Cancel',
    //     type: 'warning'
    //   })
    //     .then(async () => {
    //       await deleteParkingLot({id: row.id}).then(res => {
    //         if (res.code === '0') {
    //           this.$message({
    //             message: 'Delete Successfully',
    //             type: 'success',
    //           })
    //           this.handleReset()
    //         }
    //       })
    //     })
    //     .catch(err => {
    //       console.error(err)
    //     })
    // },

    // handleDelete({ $index, row }) {
    //   this.$confirm('Confirm to remove the role?', 'Warning', {
    //     confirmButtonText: 'Confirm',
    //     cancelButtonText: 'Cancel',
    //     type: 'warning'
    //   })
    //     .then(async() => {
    //       await deleteRole(row.key)
    //       this.rolesList.splice($index, 1)
    //       this.$message({
    //         type: 'success',
    //         message: 'Delete succed!'
    //       })
    //     })
    //     .catch(err => { console.error(err) })
    // },

    handleJoin(row) {
      this.$confirm('此操作将该文件加入旅行图鉴, 是否继续?', '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
        .then(async () => {
          await fetchJoin({id: row.id}).then(res => {
            if (res.code === '0') {
              this.$message({
                type: 'success',
                message: 'Join succed!'
              })
              this.handleReset()
            }
          })
        })
        .catch(err => {
          console.error(err)
        })
    },

    handleExit(row) {
      this.$confirm('此操作将该文件退出旅行图鉴, 是否继续?', '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
        .then(async () => {
          await fetchExit({id: row.id}).then(res => {
            if (res.code === '0') {
              this.$message({
                type: 'success',
                message: 'Exit succed!'
              })
              this.handleReset()
            }
          })
        })
        .catch(err => {
          console.error(err)
        })
    },

    illustratedHandbookFormatter(row) {
      let info = null
      switch (row.illustratedHandbook) {
        case '01':
          info = '是'
          break
        case '02':
          info = '否'
          break
        default:
          info = '未知'
          break
      }
      return info
    },

    handleFilter() {
      this.listQuery.pageNum = 1
      this.getList()
    },
    refresh() {
      this.dialogFormVisible = false
      this.dialogAuditVisible = false
      this.getList()
    },
    handleReset: function () {
      this.listQuery.scenicSpotName = null
      this.listQuery.startTime = null
      this.listQuery.endTime = null
      this.getList()
    }

  }
}
</script>
<style lang="scss" scoped>

</style>
