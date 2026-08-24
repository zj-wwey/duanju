(function() {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var accent3 = style.getPropertyValue('--accent3').trim();
  var warn = style.getPropertyValue('--warn').trim();
  var danger = style.getPropertyValue('--danger').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();
  var bg3 = style.getPropertyValue('--bg3').trim();

  // ===== Chart 1: Project Readiness Radar =====
  var radarEl = document.getElementById('chart-radar');
  if (radarEl) {
    var radar = echarts.init(radarEl, null, { renderer: 'svg' });
    radar.setOption({
      animation: false,
      title: {
        text: '项目各维度就绪度评估',
        left: 'center',
        top: 10,
        textStyle: { fontSize: 16, fontWeight: 700, color: ink }
      },
      tooltip: {
        trigger: 'item',
        appendToBody: true
      },
      legend: {
        bottom: 10,
        data: ['当前状态', '上线最低标准'],
        textStyle: { color: muted, fontSize: 13 }
      },
      radar: {
        center: ['50%', '52%'],
        radius: '62%',
        indicator: [
          { name: '后端功能', max: 100 },
          { name: '前端功能', max: 100 },
          { name: '多语言支持', max: 100 },
          { name: '安全配置', max: 100 },
          { name: '核心测试', max: 100 },
          { name: '容器化', max: 100 },
          { name: 'CI/CD', max: 100 },
          { name: '监控运维', max: 100 },
          { name: '文档', max: 100 }
        ],
        axisName: {
          color: ink,
          fontSize: 12,
          fontWeight: 600
        },
        splitArea: {
          areaStyle: {
            color: ['rgba(37, 99, 235, 0.02)', 'rgba(37, 99, 235, 0.04)', 'rgba(37, 99, 235, 0.06)', 'rgba(37, 99, 235, 0.08)', 'rgba(37, 99, 235, 0.10)']
          }
        },
        splitLine: { lineStyle: { color: rule } },
        axisLine: { lineStyle: { color: rule } }
      },
      series: [{
        type: 'radar',
        data: [
          {
            value: [95, 90, 95, 45, 15, 0, 0, 30, 25],
            name: '当前状态',
            areaStyle: { color: 'rgba(37, 99, 235, 0.15)' },
            lineStyle: { color: accent, width: 2 },
            itemStyle: { color: accent },
            symbolSize: 6
          },
          {
            value: [90, 90, 90, 90, 60, 100, 80, 70, 50],
            name: '上线最低标准',
            areaStyle: { color: 'rgba(124, 58, 237, 0.08)' },
            lineStyle: { color: accent2, width: 2, type: 'dashed' },
            itemStyle: { color: accent2 },
            symbolSize: 6
          }
        ]
      }]
    });
    window.addEventListener('resize', function() { radar.resize(); });
  }

  // ===== Chart 2: Gantt Timeline (12 weeks = 84 days) =====
  var ganttEl = document.getElementById('chart-gantt');
  if (ganttEl) {
    var gantt = echarts.init(ganttEl, null, { renderer: 'svg' });

    var phases = [
      { name: '前端 .env + .gitignore + README', start: 0,  duration: 5,  color: accent3 },
      { name: '异常处理优化 + mock-pay 隔离',     start: 3,  duration: 5,  color: accent3 },
      { name: 'application-prod.yml + 密钥清理',   start: 10, duration: 7,  color: danger },
      { name: 'CORS 白名单 + Redis 密码',          start: 14, duration: 5,  color: danger },
      { name: 'HTTPS 配置 + 支付回调验证',         start: 17, duration: 7,  color: danger },
      { name: '支付/积分单元测试',                  start: 24, duration: 10, color: accent },
      { name: '认证授权单元测试',                   start: 31, duration: 7,  color: accent },
      { name: '核心流程手动验证',                   start: 36, duration: 7,  color: accent },
      { name: '后端 Dockerfile',                    start: 42, duration: 5,  color: accent2 },
      { name: 'Nginx 配置 + 前端构建脚本',          start: 45, duration: 5,  color: accent2 },
      { name: 'docker-compose 编排',                start: 48, duration: 4,  color: accent2 },
      { name: 'GitHub Actions CI',                  start: 52, duration: 7,  color: warn },
      { name: '手动部署脚本 + Staging 验证',        start: 56, duration: 7,  color: warn },
      { name: '生产部署 + SSL + DNS',               start: 70, duration: 5,  color: danger },
      { name: '冒烟测试 + 监控配置',                start: 73, duration: 6,  color: accent3 },
      { name: '正式上线',                           start: 80, duration: 4,  color: accent3 }
    ];

    var categories = phases.map(function(p) { return p.name; }).reverse();

    gantt.setOption({
      animation: false,
      title: {
        text: '单人开发 12 周甘特图',
        left: 'center',
        top: 10,
        textStyle: { fontSize: 16, fontWeight: 700, color: ink }
      },
      tooltip: {
        trigger: 'item',
        appendToBody: true,
        formatter: function(params) {
          var d = params.data;
          var weekStart = Math.floor(d.value[0] / 7) + 1;
          var weekEnd = Math.ceil((d.value[0] + d.value[2]) / 7);
          return '<b>' + d.name + '</b><br/>第 ' + weekStart + ' 周 ~ 第 ' + weekEnd + ' 周<br/>工期: ' + d.value[2] + ' 天';
        }
      },
      grid: {
        left: 220,
        right: 40,
        top: 50,
        bottom: 40
      },
      xAxis: {
        type: 'value',
        name: '周',
        nameLocation: 'middle',
        nameGap: 25,
        nameTextStyle: { color: muted, fontSize: 12 },
        min: 0,
        max: 84,
        interval: 7,
        axisLabel: {
          color: muted,
          fontSize: 11,
          formatter: function(val) {
            var week = Math.floor(val / 7) + 1;
            return week <= 12 ? 'W' + week : '';
          }
        },
        splitLine: { lineStyle: { color: rule, type: 'dashed' } },
        axisLine: { lineStyle: { color: rule } }
      },
      yAxis: {
        type: 'category',
        data: categories,
        axisLabel: {
          color: ink,
          fontSize: 11,
          fontWeight: 600
        },
        axisLine: { lineStyle: { color: rule } },
        axisTick: { show: false }
      },
      series: [{
        type: 'custom',
        renderItem: function(params, api) {
          var categoryIndex = api.value(1);
          var start = api.coord([api.value(0), categoryIndex]);
          var end = api.coord([api.value(0) + api.value(2), categoryIndex]);
          var height = api.size([0, 1])[1] * 0.55;

          var rectShape = echarts.graphic.clipRectByRect({
            x: start[0],
            y: start[1] - height / 2,
            width: end[0] - start[0],
            height: height
          }, {
            x: params.coordSys.x,
            y: params.coordSys.y,
            width: params.coordSys.width,
            height: params.coordSys.height
          });

          return rectShape && {
            type: 'rect',
            transition: ['shape'],
            shape: rectShape,
            style: {
              fill: api.value(3),
              stroke: 'rgba(255,255,255,0.6)',
              strokeWidth: 1,
              shadowBlur: 4,
              shadowColor: 'rgba(0,0,0,0.1)',
              borderRadius: 3
            }
          };
        },
        encode: {
          x: [0, 2],
          y: 1
        },
        data: phases.map(function(p, idx) {
          return {
            name: p.name,
            value: [p.start, phases.length - 1 - idx, p.duration, p.color],
            itemStyle: { color: p.color }
          };
        })
      }]
    });
    window.addEventListener('resize', function() { gantt.resize(); });
  }

  // ===== Chart 3: Task Distribution Bar =====
  var tasksEl = document.getElementById('chart-tasks');
  if (tasksEl) {
    var tasks = echarts.init(tasksEl, null, { renderer: 'svg' });
    tasks.setOption({
      animation: false,
      title: {
        text: '各阶段任务数量与工期',
        left: 'center',
        top: 10,
        textStyle: { fontSize: 16, fontWeight: 700, color: ink }
      },
      tooltip: {
        trigger: 'axis',
        appendToBody: true,
        axisPointer: { type: 'shadow' }
      },
      legend: {
        bottom: 5,
        data: ['任务数量', '工期（天）'],
        textStyle: { color: muted, fontSize: 13 }
      },
      grid: {
        left: 60,
        right: 50,
        top: 50,
        bottom: 50
      },
      xAxis: {
        type: 'category',
        data: ['功能完善', '安全加固', '核心测试', '容器化', 'CI/CD', '部署上线', '监控运维'],
        axisLabel: {
          color: ink,
          fontSize: 12,
          fontWeight: 600,
          rotate: 15
        },
        axisLine: { lineStyle: { color: rule } },
        axisTick: { show: false }
      },
      yAxis: [
        {
          type: 'value',
          name: '任务数',
          nameTextStyle: { color: muted, fontSize: 11 },
          axisLabel: { color: muted, fontSize: 11 },
          splitLine: { lineStyle: { color: rule, type: 'dashed' } },
          axisLine: { show: false }
        },
        {
          type: 'value',
          name: '工期（天）',
          nameTextStyle: { color: muted, fontSize: 11 },
          axisLabel: { color: muted, fontSize: 11 },
          splitLine: { show: false },
          axisLine: { show: false }
        }
      ],
      series: [
        {
          name: '任务数量',
          type: 'bar',
          data: [4, 6, 3, 5, 2, 4, 4],
          itemStyle: {
            color: accent,
            borderRadius: [4, 4, 0, 0]
          },
          barWidth: '30%'
        },
        {
          name: '工期（天）',
          type: 'bar',
          yAxisIndex: 1,
          data: [10, 14, 21, 10, 14, 7, 7],
          itemStyle: {
            color: accent2,
            borderRadius: [4, 4, 0, 0]
          },
          barWidth: '30%'
        }
      ]
    });
    window.addEventListener('resize', function() { tasks.resize(); });
  }

  // ===== Mermaid Init =====
  if (typeof mermaid !== 'undefined') {
    mermaid.initialize({
      startOnLoad: true,
      theme: 'neutral',
      securityLevel: 'loose',
      flowchart: {
        curve: 'basis',
        padding: 16,
        nodeSpacing: 40,
        rankSpacing: 40
      },
      themeVariables: {
        primaryColor: bg2,
        primaryTextColor: ink,
        primaryBorderColor: rule,
        lineColor: accent,
        secondaryColor: bg3,
        tertiaryColor: bg2,
        fontFamily: 'InstrumentSans, sans-serif',
        fontSize: '14px'
      }
    });
  }
})();
