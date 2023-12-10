package com.scscscc.ranging;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.scscscc.ranging.databinding.FragmentFirstBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    public Handler handler;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        handler = new Handler(
                msg -> {
                    if (msg.what == 0)
                        binding.textviewOut0.setText(msg.obj.toString());
                    else if (msg.what == 1)
                        binding.textviewOut1.setText(msg.obj.toString());
                    else if (msg.what == 2)
                        binding.textviewOut2.setText(msg.obj.toString());
                    else if (msg.what == 3)
                        binding.textviewOut3.setText(msg.obj.toString());
                    else if (msg.what == 4)
                        binding.textviewOut4.setText(msg.obj.toString());
                    else if (msg.what == 5)
                        if (msg.arg2 == 1)
                            binding.textviewOut5.setText(binding.textviewOut5.getText() + msg.obj.toString() + "\n");
                        else
                            binding.textviewOut5.setText(msg.obj.toString());
                    return true;
                }
        );
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        TheBrain.init(handler, getContext());
        binding.textviewOut0.setText("sample rate = " + TheBrain.sampleRate);
        MyRecorder.init(handler);
        MyPlayer.init();
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.buttonDebug1.setOnClickListener(view1 -> {
            String message = "hello " + (int) Math.round(Math.random() * 100);
            BluetoothService.sendMessage(message);
        });
        binding.buttonDebug2.setOnClickListener(view110 -> MyPlayer.beep(true));
        binding.buttonStartPlay.setOnClickListener(view19 -> binding.textviewOut0.setText("not implemented"));
        binding.buttonEndPlay.setOnClickListener(view18 -> {
            binding.textviewOut0.setText("Stopped Playing");
            MyPlayer.stopPlaying();
        });
        binding.buttonStartListen.setOnClickListener(view17 -> {
            binding.textviewOut0.setText("Started Listening");
            MyRecorder.startRecording(getContext());
        });
        binding.buttonEndListen.setOnClickListener(view15 -> {
            binding.textviewOut0.setText("Ended Listening");
            MyRecorder.stopRecording();
        });
        binding.buttonBluetoothServer.setOnClickListener(view16 -> {
            binding.textviewOut0.setText("Bluetooth Server");
            BluetoothService.init(handler, true);
            BluetoothService.connect(getContext());
        });
        binding.buttonBluetoothClient.setOnClickListener(view13 -> {
            binding.textviewOut0.setText("Bluetooth Client");
            BluetoothService.init(handler, false);
            BluetoothService.connect(getContext());
        });
        binding.buttonFirst.setOnClickListener(view12 -> NavHostFragment.findNavController(FirstFragment.this)
                .navigate(R.id.action_FirstFragment_to_SecondFragment));
        binding.switch1.setOnCheckedChangeListener((buttonView, isChecked) -> {
            TheBrain.enable = isChecked;
        });
        binding.buttonReset.setOnClickListener(view14 -> TheBrain.reset());
        binding.buttonConfirm.setOnClickListener(view1 -> {
            String thresholdText = binding.editTextText.getText().toString();
            try {
                TheBrain.simThreshold = Double.parseDouble(thresholdText);
                File file = new File(getContext().getFilesDir(), "threshold.txt");
                if (!file.exists())
                    file.createNewFile();

                FileOutputStream fos = new FileOutputStream(file);
                String configString = String.valueOf(TheBrain.simThreshold);
                fos.write(configString.getBytes());
                fos.close();
                binding.textviewOut0.setText("Settings updated");
            } catch (NumberFormatException e) {
                binding.textviewOut0.setText("Invalid threshold");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        try {
            File file = new File(getContext().getFilesDir(), "threshold.txt");
            if (file.exists()) {
                file.createNewFile();
                byte[] buffer = new byte[1024];
                int read;
                read = getContext().openFileInput("threshold.txt").read(buffer);
                if (read > 0) {
                    String configString = new String(buffer, 0, read);
                    TheBrain.simThreshold = Double.parseDouble(configString);
                    binding.editTextText.setText(configString);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}