const express = require('express');
const fs = require('fs');
const path = require('path');
const { marked } = require('marked');
const cors = require('cors');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.static('public'));

// 笔记文件目录（相对于项目根目录的 note 文件夹）
const NOTES_DIR = path.join(__dirname, '..', '..', 'note');

// 分类图标配置（用于美化显示，不限制分类）
const CATEGORY_ICONS = {
  '基础知识': '📖',
  '预训练': '⚙️',
  '后训练': '🔄',
  '强化学习': '🎮',
  '推理': '🚀',
  '优化': '📊',
  '工具': '🛠️',
  '大语言模型': '🧠',
  '多模态': '🎨',
  '微调': '🔧',
  '其他': '📁'
};

// 从文件名提取分类（按"-"前的前缀）
function extractCategoryFromFilename(filename) {
  const basename = filename.replace('.md', '');
  const parts = basename.split('-');
  if (parts.length > 0) {
    const prefix = parts[0].trim();
    return prefix;
  }
  return '其他';
}

// 获取分类图标
function getCategoryIcon(categoryId) {
  return CATEGORY_ICONS[categoryId] || '📁';
}

// API: 获取所有分类及文档列表
app.get('/api/categories', (req, res) => {
  try {
    if (!fs.existsSync(NOTES_DIR)) {
      return res.json([]);
    }

    // 动态提取所有分类
    const categoryMap = new Map();

    const files = fs.readdirSync(NOTES_DIR).filter(file => file.endsWith('.md'));

    files.forEach(file => {
      const categoryId = extractCategoryFromFilename(file);
      if (!categoryMap.has(categoryId)) {
        categoryMap.set(categoryId, {
          id: categoryId,
          name: categoryId,
          icon: getCategoryIcon(categoryId),
          count: 0
        });
      }
      categoryMap.get(categoryId).count++;
    });

    // 转换为数组并返回
    res.json(Array.from(categoryMap.values()));
  } catch (error) {
    console.error('Error reading categories:', error);
    res.status(500).json({ error: 'Failed to read categories' });
  }
});

// API: 获取指定分类下的文档列表
app.get('/api/category/:categoryId', (req, res) => {
  try {
    const categoryId = req.params.categoryId;

    if (!fs.existsSync(NOTES_DIR)) {
      return res.json([]);
    }

    const files = fs.readdirSync(NOTES_DIR)
      .filter(file => file.endsWith('.md'))
      .map(file => {
        const filePath = path.join(NOTES_DIR, file);
        const stats = fs.statSync(filePath);
        const content = fs.readFileSync(filePath, 'utf-8');
        const firstLine = content.split('\n')[0] || '';
        const title = firstLine.startsWith('#') ? firstLine.replace(/^#+\s*/, '') : file.replace('.md', '');
        const fileCategoryId = extractCategoryFromFilename(file);

        return {
          filename: file,
          title: title.trim(),
          category: fileCategoryId,
          categoryIcon: getCategoryIcon(fileCategoryId),
          modifiedTime: stats.mtime.toISOString(),
          modifiedTimeFormatted: stats.mtime.toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
          }),
          preview: content.substring(0, 150).replace(/#/g, '').trim()
        };
      })
      .filter(file => file.category === categoryId);

    files.sort((a, b) => new Date(b.modifiedTime) - new Date(a.modifiedTime));

    res.json(files);
  } catch (error) {
    console.error('Error reading category notes:', error);
    res.status(500).json({ error: 'Failed to read category notes' });
  }
});

// API: 获取所有 markdown 文件列表
app.get('/api/notes', (req, res) => {
  try {
    if (!fs.existsSync(NOTES_DIR)) {
      return res.json([]);
    }

    const files = fs.readdirSync(NOTES_DIR)
      .filter(file => file.endsWith('.md'))
      .map(file => {
        const filePath = path.join(NOTES_DIR, file);
        const stats = fs.statSync(filePath);
        const content = fs.readFileSync(filePath, 'utf-8');
        const firstLine = content.split('\n')[0] || '';
        const title = firstLine.startsWith('#') ? firstLine.replace(/^#+\s*/, '') : file.replace('.md', '');
        const categoryId = extractCategoryFromFilename(file);

        return {
          filename: file,
          title: title.trim(),
          preview: content.substring(0, 200).replace(/#/g, '').trim(),
          modifiedTime: stats.mtime.toISOString(),
          modifiedTimeFormatted: stats.mtime.toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
          }),
          category: categoryId,
          categoryIcon: getCategoryIcon(categoryId)
        };
      });

    res.json(files);
  } catch (error) {
    console.error('Error reading notes:', error);
    res.status(500).json({ error: 'Failed to read notes' });
  }
});

// API: 获取单个 markdown 文件内容
app.get('/api/notes/:filename', (req, res) => {
  try {
    const filename = req.params.filename;
    const filePath = path.join(NOTES_DIR, filename);
    
    if (!fs.existsSync(filePath)) {
      return res.status(404).json({ error: 'Note not found' });
    }
    
    const content = fs.readFileSync(filePath, 'utf-8');
    const html = marked.parse(content);
    
    res.json({ filename, content, html });
  } catch (error) {
    console.error('Error reading note:', error);
    res.status(500).json({ error: 'Failed to read note' });
  }
});

app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, '0.0.0.0', () => {
  console.log('📚 LLM-to-Agent Web 服务已启动');
  console.log('   访问地址：http://localhost:' + PORT);
  console.log('   笔记目录：' + NOTES_DIR);
});
