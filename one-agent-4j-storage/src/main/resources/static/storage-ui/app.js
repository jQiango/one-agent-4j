const { createApp } = Vue;

createApp({
    data() {
        return {
            // 存储配置
            configs: [],
            selectedConfig: null,
            showConfigModal: false,
            configForm: {
                name: '',
                type: 'S3',
                endpoint: '',
                accessKeyId: '',
                accessKeySecret: '',
                region: '',
                defaultBucket: '',
                enabled: true
            },
            
            // 存储桶
            buckets: [],
            selectedBucket: '',
            
            // 文件管理
            files: [],
            currentPath: '',
            loading: false,
            
            // 分页相关
            pagination: {
                hasMore: false,
                nextContinuationToken: null,
                currentCount: 0,
                pageSize: 50,
                loading: false
            },

            // 上传
            showUploadModal: false,
            uploadFiles: [],
            uploadProgress: 0,
            dragOver: false,
            
            // 文件夹
            showFolderModal: false,
            folderName: '',
            
            // API基础URL
            apiBaseUrl: '/api/storage',

            // 文件搜索
            searchKeyword: '',
            searchResults: [],
            showSearchResults: false,

            // 批量操作
            selectedFiles: [],
            showBatchActions: false,

            // 文件预览
            previewFile: null,
            showPreviewModal: false,
        };
    },
    
    computed: {
        pathParts() {
            if (!this.currentPath) return [];
            return this.currentPath.split('/').filter(part => part);
        },

        // 分离文件夹和文件
        folders() {
            return this.files.filter(item => item.isFolder);
        },

        fileList() {
            return this.files.filter(item => !item.isFolder);
        },

        sortedFiles() {
            // 文件夹优先排序
            return (this.files || []).slice().sort((a, b) => {
                if (a.isFolder && !b.isFolder) return -1;
                if (!a.isFolder && b.isFolder) return 1;
                return 0;
            });
        }
    },
    
    mounted() {
        this.loadConfigs();
        this.initModals();
    },
    
    methods: {
        // 初始化模态框
        initModals() {
            // 监听模态框显示/隐藏事件
            const modals = ['configModal', 'uploadModal', 'folderModal'];
            modals.forEach(modalId => {
                const modal = document.getElementById(modalId);
                if (modal) {
                    modal.addEventListener('hidden.bs.modal', () => {
                        this.resetForms();
                    });
                }
            });
        },
        
        // 重置表单
        resetForms() {
            this.configForm = {
                name: '',
                type: 'S3',
                endpoint: '',
                accessKeyId: '',
                accessKeySecret: '',
                region: '',
                defaultBucket: '',
                enabled: true
            };
            this.uploadFiles = [];
            this.uploadProgress = 0;
            this.folderName = '';
        },
        
        // 加载存储配置
        async loadConfigs() {
            try {
                const response = await axios.get(`${this.apiBaseUrl}/config/list`);
                if (response.data.success) {
                    this.configs = response.data.data;
                    if (this.configs.length > 0) {
                        this.selectConfig(this.configs[0]);
                    }
                } else {
                    this.showError(response.data.message || '加载配置失败');
                }
            } catch (error) {
                console.error('加载配置失败:', error);
                const errorMessage = error.response?.data?.message || error.message || '加载配置失败';
                this.showError(errorMessage);
            }
        },
        
        // 选择存储配置
        async selectConfig(config) {
            this.selectedConfig = config;
            this.selectedBucket = '';
            this.currentPath = '';
            this.files = [];
            
            if (config) {
                await this.loadBuckets();
            }
        },
        
        // 加载存储桶
        async loadBuckets() {
            if (!this.selectedConfig) return;
            
            try {
                const response = await axios.get(`${this.apiBaseUrl}/buckets/${this.selectedConfig.id}`);
                if (response.data.success) {
                    this.buckets = response.data.data;
                    // 清空当前选择和数据
                    this.selectedBucket = '';
                    this.currentPath = '';
                    this.files = [];
                    this.pagination.nextContinuationToken = null;
                    this.pagination.hasMore = false;
                    this.pagination.currentCount = 0;
                } else {
                    this.showError(response.data.message || '加载存储桶失败');
                }
            } catch (error) {
                console.error('加载存储桶失败:', error);
                const errorMessage = error.response?.data?.message || error.message || '加载存储桶失败';
                this.showError(errorMessage);
            }
        },
        
        // 存储桶切换处理
        onBucketChange() {
            if (this.selectedBucket) {
                this.currentPath = '';
                this.files = [];
                this.pagination.nextContinuationToken = null;
                this.pagination.hasMore = false;
                this.pagination.currentCount = 0;
                this.loadFiles();
            } else {
                this.files = [];
                this.currentPath = '';
            }
        },
        
        // 加载文件列表（支持分页）
        async loadFiles(loadMore = false) {
            if (!this.selectedConfig || !this.selectedBucket) return;
            
            if (!loadMore) {
                this.loading = true;
                this.files = [];
                this.pagination.nextContinuationToken = null;
                this.pagination.currentCount = 0;
            } else {
                this.pagination.loading = true;
            }

            try {
                const requestData = {
                    configId: this.selectedConfig.id,
                    bucketName: this.selectedBucket,
                    prefix: this.currentPath || '',
                    delimiter: '/',
                    pageSize: this.pagination.pageSize
                };

                // 如果是加载更多，添加continuationToken
                if (loadMore && this.pagination.nextContinuationToken) {
                    requestData.continuationToken = this.pagination.nextContinuationToken;
                }

                const response = await axios.post(`${this.apiBaseUrl}/files/list`, requestData);

                if (response.data.success) {
                    const data = response.data.data;
                    const newFiles = [];

                    // 处理后端返回的文件夹数据
                    if (data.folders && data.folders.length > 0) {
                        data.folders.forEach(folder => {
                            newFiles.push({
                                key: folder.key,
                                name: folder.name,
                                isFolder: true,
                                lastModified: new Date(),
                                size: 0
                            });
                        });
                    }

                    // 处理后端返回的文件数据
                    if (data.files && data.files.length > 0) {
                        console.log('当前路径:', this.currentPath);
                        console.log('文件总数:', data.files.length);
                        data.files.forEach(fileObj => {
                            // 提取文件名（去掉路径前缀）
                            let fileName = fileObj.key;
                            if (this.currentPath && fileName.startsWith(this.currentPath)) {
                                fileName = fileName.substring(this.currentPath.length);
                            }
                            
                            // 移除开头的斜杠
                            if (fileName.startsWith('/')) {
                                fileName = fileName.substring(1);
                            }
                            
                            // 只显示当前目录下的文件，不显示子目录中的文件
                            if (fileName && !fileName.includes('/')) {
                                newFiles.push({
                                    key: fileObj.key,
                                    name: fileName,
                                    size: fileObj.size || 0,
                                    lastModified: new Date(fileObj.lastModified || Date.now()),
                                    isFolder: false,
                                    storageClass: fileObj.storageClass || 'STANDARD'
                                });
                            } else if (fileName && fileName.includes('/')) {
                                // 如果文件名包含斜杠，说明是子目录中的文件，应该被过滤掉
                                console.warn('跳过子目录文件:', fileName);
                            } else {
                                // 空文件名的情况
                                console.warn('跳过空文件名文件:', fileObj.key);
                            }
                        });
                    }

                    // 更新分页信息
                    if (data.pagination) {
                        this.pagination.hasMore = data.pagination.hasMore;
                        this.pagination.nextContinuationToken = data.pagination.nextContinuationToken;
                        this.pagination.currentCount = (this.pagination.currentCount || 0) + (data.pagination.currentCount || 0);
                    } else {
                        // 兼容旧格式
                        this.pagination.hasMore = data.isTruncated || false;
                        this.pagination.nextContinuationToken = data.nextContinuationToken;
                    }

                    // 合并文件列表
                    if (loadMore) {
                        // 加载更多时，只添加文件，不重复添加文件夹
                        const existingFolders = this.files.filter(item => item.isFolder);
                        const existingFiles = this.files.filter(item => !item.isFolder);
                        const newFolders = newFiles.filter(item => item.isFolder);
                        const newFilesOnly = newFiles.filter(item => !item.isFolder);
                        
                        // 合并文件夹（去重）
                        const allFolders = [...existingFolders];
                        for (const newFolder of newFolders) {
                            if (!allFolders.some(folder => folder.key === newFolder.key)) {
                                allFolders.push(newFolder);
                            }
                        }
                        
                        // 合并文件
                        this.files = [...allFolders, ...existingFiles, ...newFilesOnly];
                    } else {
                        this.files = newFiles;
                    }

                } else {
                    this.showError(response.data.message || '加载文件列表失败');
                }
            } catch (error) {
                console.error('加载文件列表失败:', error);
                const errorMessage = error.response?.data?.message || error.message || '加载文件列表失败';
                this.showError(errorMessage);
            } finally {
                this.loading = false;
                this.pagination.loading = false;
            }
        },
        
        // 加载更多文件
        async loadMoreFiles() {
            if (this.pagination.hasMore && !this.pagination.loading) {
                await this.loadFiles(true);
            }
        },
        
        // 重置分页并重新加载
        async refreshFiles() {
            this.pagination.nextContinuationToken = null;
            this.pagination.hasMore = false;
            await this.loadFiles(false);
        },
        
        // 保存配置
        async saveConfig() {
            try {
                const response = await axios.post(`${this.apiBaseUrl}/config`, this.configForm);
                if (response.data.success) {
                    this.showSuccess('配置保存成功');
                    this.showConfigModal = false;
                    // 关闭模态框
                    const modal = bootstrap.Modal.getInstance(document.getElementById('configModal'));
                    if (modal) {
                        modal.hide();
                    }
                    await this.loadConfigs();
                } else {
                    this.showError(response.data.message || '保存配置失败');
                }
            } catch (error) {
                console.error('保存配置失败:', error);
                const errorMessage = error.response?.data?.message || error.message || '保存配置失败';
                this.showError(errorMessage);
            }
        },
        
        // 处理文件选择
        handleFileSelect(event) {
            const files = Array.from(event.target.files);
            this.uploadFiles = files;
        },
        
        // 处理文件拖拽
        handleFileDrop(event) {
            this.dragOver = false;
            const files = Array.from(event.dataTransfer.files);
            this.uploadFiles = files;
        },
        
        // 上传文件
        async uploadFiles() {
            if (!this.selectedConfig || this.uploadFiles.length === 0) return;
            
            this.uploadProgress = 0;
            const totalFiles = this.uploadFiles.length;
            let uploadedCount = 0;
            
            for (const file of this.uploadFiles) {
                try {
                    const formData = new FormData();
                    formData.append('file', file);
                    formData.append('configId', this.selectedConfig.id);
                    formData.append('bucketName', this.selectedBucket);
                    
                    if (this.currentPath) {
                        formData.append('objectKey', this.currentPath + file.name);
                    }
                    
                    const response = await axios.post(`${this.apiBaseUrl}/upload`, formData, {
                        headers: {
                            'Content-Type': 'multipart/form-data'
                        }
                    });
                    
                    if (response.data.success) {
                        uploadedCount++;
                        this.uploadProgress = Math.round((uploadedCount / totalFiles) * 100);
                    } else {
                        this.showError(`上传文件 ${file.name} 失败: ${response.data.message}`);
                    }
                } catch (error) {
                    console.error('上传文件失败:', error);
                    const errorMessage = error.response?.data?.message || error.message || `上传文件 ${file.name} 失败`;
                    this.showError(errorMessage);
                }
            }
            
            if (uploadedCount === totalFiles) {
                this.showSuccess('文件上传成功');
                this.showUploadModal = false;
                // 关闭模态框
                const modal = bootstrap.Modal.getInstance(document.getElementById('uploadModal'));
                if (modal) {
                    modal.hide();
                }
                this.loadFiles();
            }
        },
        
        // 下载文件
        async downloadFile(file) {
            try {
                // 直接通过对象键下载文件
                const response = await axios.get(`${this.apiBaseUrl}/download`, {
                    params: {
                        configId: this.selectedConfig.id,
                        bucketName: this.selectedBucket,
                        objectKey: file.key
                    },
                    responseType: 'blob'
                });
                
                // 检查响应状态
                if (response.status === 200) {
                    const url = window.URL.createObjectURL(new Blob([response.data]));
                    const link = document.createElement('a');
                    link.href = url;
                    link.setAttribute('download', file.name);
                    document.body.appendChild(link);
                    link.click();
                    link.remove();
                    window.URL.revokeObjectURL(url);
                } else {
                    this.showError('下载文件失败');
                }
            } catch (error) {
                console.error('下载文件失败:', error);
                const errorMessage = error.response?.data?.message || error.message || '下载文件失败';
                this.showError(errorMessage);
            }
        },
        
        // 删除文件
        async deleteFile(file) {
            if (!confirm(`确定要删除 ${file.name} 吗？`)) return;
            
            try {
                // 直接通过对象键删除文件
                const response = await axios.delete(`${this.apiBaseUrl}/files`, {
                    params: {
                        configId: this.selectedConfig.id,
                        bucketName: this.selectedBucket,
                        objectKey: file.key
                    }
                });
                
                if (response.data.success) {
                    this.showSuccess('文件删除成功');
                    this.loadFiles();
                } else {
                    this.showError(response.data.message || '删除文件失败');
                }
            } catch (error) {
                console.error('删除文件失败:', error);
                const errorMessage = error.response?.data?.message || error.message || '删除文件失败';
                this.showError(errorMessage);
            }
        },
        
        // 创建文件夹
        async createFolder() {
            if (!this.selectedConfig || !this.folderName.trim()) return;
            
            try {
                const folderPath = this.currentPath + this.folderName.trim() + '/';
                const response = await axios.post(`${this.apiBaseUrl}/folder`, null, {
                    params: {
                        configId: this.selectedConfig.id,
                        bucketName: this.selectedBucket,
                        folderPath: folderPath
                    }
                });
                
                if (response.data.success) {
                    this.showSuccess('文件夹创建成功');
                    this.showFolderModal = false;
                    // 关闭模态框
                    const modal = bootstrap.Modal.getInstance(document.getElementById('folderModal'));
                    if (modal) {
                        modal.hide();
                    }
                    this.loadFiles();
                } else {
                    this.showError(response.data.message || '创建文件夹失败');
                }
            } catch (error) {
                console.error('创建文件夹失败:', error);
                const errorMessage = error.response?.data?.message || error.message || '创建文件夹失败';
                this.showError(errorMessage);
            }
        },

        // 文件搜索
        async searchFiles() {
            if (!this.selectedConfig || !this.searchKeyword.trim()) return;

            const keyword = this.searchKeyword.trim();
            this.showSearchResults = false;
            this.searchResults = [];

            try {
                const response = await axios.get(`${this.apiBaseUrl}/search`, {
                    params: {
                        configId: this.selectedConfig.id,
                        bucketName: this.selectedBucket,
                        keyword: keyword,
                        maxResults: 100
                    }
                });

                if (response.data.success) {
                    const data = response.data.data;
                    this.searchResults = data.files || [];
                    this.showSearchResults = this.searchResults.length > 0;
                } else {
                    this.showError(response.data.message || '搜索文件失败');
                }
            } catch (error) {
                console.error('搜索文件失败:', error);
                const errorMessage = error.response?.data?.message || error.message || '搜索文件失败';
                this.showError(errorMessage);
            }
        },

        // 批量选择文件
        toggleFileSelection(file) {
            const index = this.selectedFiles.findIndex(f => f.key === file.key);
            if (index > -1) {
                // 已选择，取消选择
                this.selectedFiles.splice(index, 1);
            } else {
                // 未选择，添加到选择中
                this.selectedFiles.push(file);
            }
        },

        // 批量删除文件
        async batchDeleteFiles() {
            if (this.selectedFiles.length === 0 || !confirm('确定要删除选中的文件吗？')) return;

            const deletePromises = this.selectedFiles.map(file => {
                return axios.delete(`${this.apiBaseUrl}/files`, {
                    params: {
                        configId: this.selectedConfig.id,
                        bucketName: this.selectedBucket,
                        objectKey: file.key
                    }
                });
            });

            try {
                const responses = await Promise.all(deletePromises);
                const success = responses.every(response => response.data.success);

                if (success) {
                    this.showSuccess('文件删除成功');
                    this.selectedFiles = [];
                    this.loadFiles();
                } else {
                    this.showError('部分文件删除失败');
                }
            } catch (error) {
                console.error('批量删除文件失败:', error);
                this.showError('批量删除文件失败');
            }
        },

        // 文件预览
        async previewFile(file) {
            if (!file) return;

            this.previewFile = null;
            this.showPreviewModal = false;

            try {
                // 直接通过对象键获取文件预览
                const response = await axios.get(`${this.apiBaseUrl}/preview`, {
                    params: {
                        configId: this.selectedConfig.id,
                        bucketName: this.selectedBucket,
                        objectKey: file.key
                    },
                    responseType: 'blob'
                });

                if (response.status === 200) {
                    const url = window.URL.createObjectURL(new Blob([response.data]));
                    this.previewFile = {
                        url: url,
                        name: file.name,
                        type: file.type
                    };
                    this.showPreviewModal = true;
                } else {
                    this.showError('获取文件预览失败');
                }
            } catch (error) {
                console.error('获取文件预览失败:', error);
                const errorMessage = error.response?.data?.message || error.message || '获取文件预览失败';
                this.showError(errorMessage);
            }
        },

        // 返回上级目录
        goBack() {
            if (!this.currentPath) return;

            const pathParts = this.currentPath.split('/').filter(part => part);
            if (pathParts.length <= 1) {
                this.navigateToPath('');
            } else {
                const parentPath = pathParts.slice(0, -1).join('/') + '/';
                this.navigateToPath(parentPath);
            }
        },
        
        // 导航到指定路径
        navigateToPath(path) {
            this.currentPath = path;
            this.loadFiles();
        },
        
        // 获取到指定索引的路径
        getPathUpTo(index) {
            const pathParts = this.currentPath.split('/').filter(part => part);
            if (index >= 0 && index < pathParts.length) {
                return pathParts.slice(0, index + 1).join('/') + '/';
            }
            return '';
        },

        // 获��文件图标
        getFileIcon(filename) {
            if (!filename) return 'bi bi-file-earmark';

            const extension = filename.toLowerCase().split('.').pop();

            const iconMap = {
                // 图片
                'jpg': 'bi bi-file-earmark-image text-primary',
                'jpeg': 'bi bi-file-earmark-image text-primary',
                'png': 'bi bi-file-earmark-image text-primary',
                'gif': 'bi bi-file-earmark-image text-primary',
                'svg': 'bi bi-file-earmark-image text-primary',
                'webp': 'bi bi-file-earmark-image text-primary',

                // 文档
                'pdf': 'bi bi-file-earmark-pdf text-danger',
                'doc': 'bi bi-file-earmark-word text-primary',
                'docx': 'bi bi-file-earmark-word text-primary',
                'xls': 'bi bi-file-earmark-excel text-success',
                'xlsx': 'bi bi-file-earmark-excel text-success',
                'ppt': 'bi bi-file-earmark-ppt text-warning',
                'pptx': 'bi bi-file-earmark-ppt text-warning',

                // 文本
                'txt': 'bi bi-file-earmark-text text-secondary',
                'md': 'bi bi-file-earmark-text text-secondary',
                'json': 'bi bi-file-earmark-code text-info',
                'xml': 'bi bi-file-earmark-code text-info',
                'html': 'bi bi-file-earmark-code text-info',
                'css': 'bi bi-file-earmark-code text-info',
                'js': 'bi bi-file-earmark-code text-warning',

                // 压缩包
                'zip': 'bi bi-file-earmark-zip text-secondary',
                'rar': 'bi bi-file-earmark-zip text-secondary',
                '7z': 'bi bi-file-earmark-zip text-secondary',
                'tar': 'bi bi-file-earmark-zip text-secondary',
                'gz': 'bi bi-file-earmark-zip text-secondary',

                // 视频
                'mp4': 'bi bi-file-earmark-play text-danger',
                'avi': 'bi bi-file-earmark-play text-danger',
                'mov': 'bi bi-file-earmark-play text-danger',
                'wmv': 'bi bi-file-earmark-play text-danger',

                // 音频
                'mp3': 'bi bi-file-earmark-music text-purple',
                'wav': 'bi bi-file-earmark-music text-purple',
                'flac': 'bi bi-file-earmark-music text-purple',
                'm4a': 'bi bi-file-earmark-music text-purple'
            };

            return iconMap[extension] || 'bi bi-file-earmark text-secondary';
        },

        // 格式化文件大小
        formatFileSize(bytes) {
            if (bytes === 0) return '0 B';
            const k = 1024;
            const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
        },
        
        // 格式化日期
        formatDate(date) {
            if (!date) return '';
            return new Date(date).toLocaleString('zh-CN');
        },
        
        // 显示成功消息
        showSuccess(message) {
            // 使用Bootstrap的toast
            const toast = document.createElement('div');
            toast.className = 'toast align-items-center text-white bg-success border-0';
            toast.setAttribute('role', 'alert');
            toast.innerHTML = `
                <div class="d-flex">
                    <div class="toast-body">
                        ${message}
                    </div>
                    <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
                </div>
            `;
            
            const container = document.getElementById('toastContainer') || document.body;
            container.appendChild(toast);
            
            const bsToast = new bootstrap.Toast(toast);
            bsToast.show();
            
            // 自动移除
            toast.addEventListener('hidden.bs.toast', () => {
                container.removeChild(toast);
            });
        },
        
        // 显示错误消息
        showError(message) {
            // 使用Bootstrap的toast
            const toast = document.createElement('div');
            toast.className = 'toast align-items-center text-white bg-danger border-0';
            toast.setAttribute('role', 'alert');
            toast.innerHTML = `
                <div class="d-flex">
                    <div class="toast-body">
                        错误: ${message}
                    </div>
                    <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
                </div>
            `;
            
            const container = document.getElementById('toastContainer') || document.body;
            container.appendChild(toast);
            
            const bsToast = new bootstrap.Toast(toast);
            bsToast.show();
            
            // 自动移除
            toast.addEventListener('hidden.bs.toast', () => {
                container.removeChild(toast);
            });
        }
    }
}).mount('#app');
