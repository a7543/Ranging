package com.scscscc.ranging;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.scscscc.ranging.databinding.FragmentFirstBinding;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    MyRecorder myRecorder;
    MyPlayer myPlayer;
    BluetoothService bluetoothService;
    TheBrain theBrain;
    public Handler handler;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        handler = new Handler() {
            public void handleMessage(Message msg) {
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
                super.handleMessage(msg);
            }
        };
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        theBrain = new TheBrain(handler);
        myRecorder = new MyRecorder(theBrain, getContext(), handler);
        myPlayer = new MyPlayer(theBrain, getContext());
        theBrain.myPlayer = myPlayer;
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.buttonDebug1.setOnClickListener(view1 -> {
            String message = "hello " + (int) Math.round(Math.random() * 100);
            bluetoothService.sendMessage(message);
        });
        binding.buttonDebug2.setOnClickListener(view110 -> myPlayer.beep(true));
        binding.buttonStartPlay.setOnClickListener(view19 -> binding.textviewOut0.setText("not implemented"));
        binding.buttonEndPlay.setOnClickListener(view18 -> {
            binding.textviewOut0.setText("Stopped Playing");
            myPlayer.stopPlaying();
        });
        binding.buttonStartListen.setOnClickListener(view17 -> {
            binding.textviewOut0.setText("Started Listening");
            myRecorder.startRecording();
        });
        binding.buttonEndListen.setOnClickListener(view15 -> {
            binding.textviewOut0.setText("Ended Listening");
            myRecorder.stopRecording();
        });
        binding.buttonBluetoothServer.setOnClickListener(view16 -> {
            binding.textviewOut0.setText("Bluetooth Server");
            bluetoothService = new BluetoothService(theBrain, getContext(), handler, true);
            theBrain.bluetoothService = bluetoothService;
            bluetoothService.connect();
        });
        binding.buttonBluetoothClient.setOnClickListener(view13 -> {
            binding.textviewOut0.setText("Bluetooth Client");
            bluetoothService = new BluetoothService(theBrain, getContext(), handler, false);
            theBrain.bluetoothService = bluetoothService;
            bluetoothService.connect();
        });
        binding.buttonFirst.setOnClickListener(view12 -> NavHostFragment.findNavController(FirstFragment.this)
                .navigate(R.id.action_FirstFragment_to_SecondFragment));
        binding.switch1.setOnCheckedChangeListener((buttonView, isChecked) -> {
            theBrain.enable = isChecked;
        });
        binding.buttonReset.setOnClickListener(view14 -> theBrain.reset());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}