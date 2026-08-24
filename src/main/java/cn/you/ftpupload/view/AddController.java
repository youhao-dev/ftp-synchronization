package cn.you.ftpupload.view;

import cn.you.ftpupload.AppContext;
import cn.you.ftpupload.pojo.FileInfo;
import cn.you.ftpupload.service.PathRulesService;
import cn.you.ftpupload.utils.TemplateResolver;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 新增、编辑和查看FTP传输规则窗口。页面支持上传/下载方向选择，并实时展示时间解析、本地目录和FTP目录示例。
 */
public class AddController {
    private static final DateTimeFormatter PREVIEW_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_DELAY_MINUTES = 52_560_000L; //最多允许偏移约 100 年，避免极端输入导致日期溢出
    @FXML
    private Label dialogTitle;
    @FXML
    private Label dialogDescription;
    @FXML
    private Label ruleId;
    @FXML
    private TextField name;
    @FXML
    private TextField filename;
    @FXML
    private ComboBox<String> transferType;
    @FXML
    private TextField basePath;
    @FXML
    private TextField rulePath;
    @FXML
    private TextField remoteBasePath;
    @FXML
    private TextField delayTime;
    @FXML
    private TextField latestFileCount;
    @FXML
    private Button chooseBasePathButton;
    @FXML
    private Button operate;
    @FXML
    private Label legacyNotice;
    @FXML
    private CheckBox legacyCompatibility;
    @FXML
    private Label previewNote;
    @FXML
    private TableView<PreviewRow> previewTable;
    @FXML
    private TableColumn<PreviewRow, String> previewTimeColumn;
    @FXML
    private TableColumn<PreviewRow, String> previewLocalColumn;
    @FXML
    private TableColumn<PreviewRow, String> previewRemoteColumn;
    private final PathRulesService pathRulesService = AppContext.getInstance().pathRulesService();
    private Mode mode = Mode.ADD;
    private Runnable dataChangeListener;

    /**
     * 初始化预览表格和实时输入监听。
     */
    @FXML
    private void initialize() {
        transferType.getItems().setAll("上传","下载");
        transferType.getSelectionModel().select("上传");
        transferType.valueProperty().addListener((observable,oldValue,newValue) -> refreshPreview());
        previewTimeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().dateTime()));
        previewLocalColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().localPath()));
        previewRemoteColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().remotePath()));
        previewTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        for (TextField field : List.of(basePath, rulePath, remoteBasePath, delayTime))
            field.textProperty().addListener((observable, oldValue, newValue) -> refreshPreview());
        legacyCompatibility.selectedProperty().addListener((observable, oldValue, newValue) -> refreshPreview());
    }

    /**
     * 初始化表单模式及规则数据。
     */
    public void initData(Mode mode, FileInfo fileInfo, Runnable dataChangeListener) {
        this.mode = mode == null ? Mode.ADD : mode;
        this.dataChangeListener = dataChangeListener;
        boolean legacy = fileInfo != null && fileInfo.usesLegacyTemplate();
        if (fileInfo != null) {
            ruleId.setText(nvl(fileInfo.getId()));
            name.setText(nvl(fileInfo.getName()));
            filename.setText(nvl(fileInfo.getFilename()));
            transferType.getSelectionModel().select(fileInfo.isDownload()?"下载":"上传");
            basePath.setText(nvl(fileInfo.getBasePath()));
            rulePath.setText(nvl(fileInfo.getRulePath()));
            remoteBasePath.setText(nvl(fileInfo.getRemoteBasePath()));
            delayTime.setText(String.valueOf(fileInfo.getDelayTime()));
            latestFileCount.setText(fileInfo.getLatestFileCount() == null ? "" : fileInfo.getLatestFileCount().toString());
        } else {
            ruleId.setText("");
            transferType.getSelectionModel().select("上传");
            delayTime.setText("0");
            latestFileCount.setText("");
        }
        legacyCompatibility.setSelected(legacy);
        legacyCompatibility.setVisible(legacy);
        legacyCompatibility.setManaged(legacy);
        legacyNotice.setVisible(legacy);
        legacyNotice.setManaged(legacy);
        applyMode();
        refreshPreview();
    }

    /**
     * 选择本地根目录。
     */
    @FXML
    private void chooseBasePath(ActionEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择本地根目录");
        File current = new File(basePath.getText().trim());
        if (current.isDirectory()) chooser.setInitialDirectory(current);
        File selected = chooser.showDialog(((Node) event.getSource()).getScene().getWindow());
        if (selected != null) basePath.setText(selected.getAbsolutePath());
    }

    /**
     * 保存新增或修改的规则。
     */
    @FXML
    private void save(ActionEvent event) {
        try {
            FileInfo fileInfo = buildAndValidate();
            if (mode == Mode.ADD) pathRulesService.addPath(fileInfo);
            else if (mode == Mode.EDIT) pathRulesService.updatePath(fileInfo);
            if (dataChangeListener != null) dataChangeListener.run();
            close(event);
        } catch (IllegalArgumentException e) {
            showWarning(e.getMessage());
        } catch (Exception e) {
            showWarning("保存失败：" + safeMessage(e));
        }
    }

    /**
     * 关闭当前规则窗口。
     */
    @FXML
    private void close(ActionEvent event) {
        ((Stage) ((Node) event.getSource()).getScene().getWindow()).close();
    }

    /**
     * 根据当前输入构建规则并执行基础校验。
     */
    private FileInfo buildAndValidate() {
        String nameText=text(name),basePathText=text(basePath),rulePathText=text(rulePath),remoteText=text(remoteBasePath),delayText=text(delayTime);
        String type="下载".equals(transferType.getValue())?FileInfo.TRANSFER_DOWNLOAD:FileInfo.TRANSFER_UPLOAD;
        if (nameText.isBlank()) throw new IllegalArgumentException("请输入规则名称");
        if (basePathText.isBlank()) throw new IllegalArgumentException("请输入本地根目录");
        if (rulePathText.isBlank()) throw new IllegalArgumentException("请输入规则路径");
        if (remoteText.isBlank()) throw new IllegalArgumentException("请输入远程根目录");
        if (rulePathText.contains("..")) throw new IllegalArgumentException("规则路径不能包含 ..，避免跳出本地根目录");
        long delay = parseDelay(delayText, true);
        Integer number = number(latestFileCount);
        if (number != null && number < 1) throw new IllegalArgumentException("请输入正确的每次检查最新文件数量，例如1、2、3、4、5");
        int templateVersion = legacyCompatibility.isManaged() && legacyCompatibility.isSelected() ? 1 : 2;
        return new FileInfo().setId(ruleId.getText()).setName(nameText).setFilename(text(filename))
                .setBasePath(basePathText).setRulePath(rulePathText).setRemoteBasePath(remoteText)
                .setDelayTime(delay).setTemplateVersion(templateVersion).setLatestFileCount(number).setTransferType(type);
    }

    /**
     * 应用新增、编辑或只读查看模式。
     */
    private void applyMode() {
        boolean view = mode == Mode.VIEW;
        dialogTitle.setText(mode==Mode.ADD?"新增传输规则":mode==Mode.EDIT?"编辑传输规则":"查看传输规则");
        dialogDescription.setText(view?"只读查看规则，并根据当前时间预览实际目录":"配置上传/下载方向、本地目录与FTP目录，底部示例会随输入实时变化");
        for (TextField field : List.of(name, filename, basePath, rulePath, remoteBasePath, delayTime, latestFileCount))
            field.setEditable(!view);
        chooseBasePathButton.setVisible(!view);
        chooseBasePathButton.setManaged(!view);
        operate.setVisible(!view);
        operate.setManaged(!view);
        transferType.setDisable(view);
        legacyCompatibility.setDisable(view);
        operate.setText(mode == Mode.ADD ? "添加规则" : "保存修改");
    }

    /**
     * 根据当前表单实时生成三组“日期时间 -> 本地目录 -> FTP目录”示例。
     */
    private void refreshPreview() {
        if (previewTable == null) return;
        long delay = parseDelay(text(delayTime), false);
        boolean legacy = legacyCompatibility != null && legacyCompatibility.isManaged() && legacyCompatibility.isSelected();
        LocalDateTime now = LocalDateTime.now();
        List<LocalDateTime> bases = List.of(now, now.plusHours(1), now.plusDays(1));
        List<PreviewRow> rows = bases.stream().map(base -> {
            LocalDateTime resolved = base.plusMinutes(delay);
            String dynamic = TemplateResolver.resolve(text(rulePath), resolved, legacy);
            return new PreviewRow(resolved.format(PREVIEW_TIME), emptyAsDash(TemplateResolver.joinLocalPreview(text(basePath), dynamic)), emptyAsDash(TemplateResolver.joinRemote(text(remoteBasePath), dynamic)));
        }).toList();
        previewTable.getItems().setAll(rows);
        String direction="下载".equals(transferType.getValue())?"下载：FTP → 本地":"上传：本地 → FTP";
        previewNote.setText(direction+"；示例已应用时间偏移 "+formatDelay(delay)+"；"+(legacy?"当前为小写占位符仍补零":"新版规则：小写不补零，大写补零"));
    }

    /**
     * 解析时间偏移；预览模式输入非法时暂按 0 处理，保存模式则抛出提示。
     */
    private long parseDelay(String value, boolean strict) {
        if (value == null || value.isBlank()) return 0;
        try {
            long delay = Long.parseLong(value.trim());
            if (delay < -MAX_DELAY_MINUTES || delay > MAX_DELAY_MINUTES) {
                if (strict) throw new IllegalArgumentException("时间偏移绝对值不能超过 " + MAX_DELAY_MINUTES + " 分钟");
                return 0;
            }
            return delay;
        } catch (NumberFormatException e) {
            if (strict) throw new IllegalArgumentException("时间偏移必须是整数分钟，例如 0、10、-5");
            return 0;
        }
    }

    /**
     * 格式化时间偏移提示。
     */
    private String formatDelay(long delay) {
        return delay == 0 ? "0 分钟" : delay > 0 ? "+" + delay + " 分钟（使用未来时间）" : delay + " 分钟（使用过去时间）";
    }

    /**
     * 获取文本框去空格值。
     */
    private String text(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    /**
     * 获取文本框去空格后转换的数字
     */
    private Integer number(TextField field) {
        if(field == null || field.getText() == null) return null;
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("请输入正确的整数数字");
        }
    }

    /**
     * null 字符串转空字符串。
     */
    private String nvl(String value) {
        return value == null ? "" : value;
    }

    /**
     * 空字符串显示为短横线。
     */
    private String emptyAsDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    /**
     * 获取异常安全信息。
     */
    private String safeMessage(Exception e) {
        return e == null ? "未知错误" : e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /**
     * 显示输入提示。
     */
    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 规则窗口模式。
     */
    public enum Mode {ADD, EDIT, VIEW}

    /**
     * 规则实时预览行。
     */
    public record PreviewRow(String dateTime, String localPath, String remotePath) {
    }
}
