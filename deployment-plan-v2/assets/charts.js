(function() {
  var style = getComputedStyle(document.documentElement);
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var chartGrid = style.getPropertyValue('--chart-grid').trim() || muted;
  var chartAxis = style.getPropertyValue('--chart-axis').trim() || muted;
  var chartLabel = style.getPropertyValue('--chart-label').trim() || muted;
  var chartSeries = [
    style.getPropertyValue('--chart-series-1').trim(),
    style.getPropertyValue('--chart-series-2').trim(),
    style.getPropertyValue('--chart-series-3').trim(),
    style.getPropertyValue('--chart-series-4').trim()
  ];
  var bg2 = style.getPropertyValue('--bg2').trim();

  // --- Gantt Chart ---
  var gantt = echarts.init(document.getElementById('chart-gantt'), null, { renderer: 'svg' });

  // Each phase: [name, start_day, end_day, color_index]
  var phases = [
    ['代码清理 & 环境变量', '08/18', '08/19', 0],
    ['Docker 容器化', '08/19', '08/20', 1],
    ['安全加固', '08/21', '08/22', 2],
    ['服务器部署 & 域名', '08/25', '08/26', 0],
    ['集成测试 & 支付验证', '08/27', '08/28', 1],
    ['上线 & 监控', '08/29', '08/29', 2]
  ];

  var dates = ['08/18', '08/19', '08/20', '08/21', '08/22', '08/25', '08/26', '08/27', '08/28', '08/29'];

  var data = [];
  phases.forEach(function(p, i) {
    var si = dates.indexOf(p[1]);
    var ei = dates.indexOf(p[2]);
    data.push({
      name: p[0],
      value: [si, ei, p[2], p[0]],
      itemStyle: { color: chartSeries[p[3]] }
    });
  });

  gantt.setOption({
    animation: false,
    tooltip: {
      appendToBody: true,
      formatter: function(p) {
        return p.name + '<br/>' + p.value[3] + ' — ' + p.value[2];
      }
    },
    grid: { left: 160, right: 30, top: 20, bottom: 20 },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: chartGrid } },
      axisTick: { show: false },
      axisLabel: { color: chartLabel, fontSize: 11, fontWeight: 600 },
      splitArea: { show: false }
    },
    yAxis: {
      type: 'category',
      data: phases.map(function(p) { return p[0]; }),
      inverse: true,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: ink, fontSize: 12, fontWeight: 600 },
      splitLine: { show: false }
    },
    series: [{
      type: 'bar',
      data: data,
      barWidth: 22,
      label: {
        show: false
      },
      emphasis: {
        itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.12)' }
      },
      encode: { x: [0, 1], y: 3 }
    }]
  });

  window.addEventListener('resize', function() { gantt.resize(); });
})();