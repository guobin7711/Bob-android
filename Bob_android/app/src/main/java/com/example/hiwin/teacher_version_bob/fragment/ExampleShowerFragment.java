package com.example.hiwin.teacher_version_bob.fragment;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.example.hiwin.teacher_version_bob.R;
import com.example.hiwin.teacher_version_bob.data.DataSpeaker;
import com.example.hiwin.teacher_version_bob.data.data.Data;

public class ExampleShowerFragment extends Fragment {
    private Data object;
    private TextView tr_sentence;
    private TextView sentence;
    private DataSpeaker speaker;

    private FragmentListener listener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_example, container, false);
        View layout = root.findViewById(R.id.example_layout);
        speaker = new DataSpeaker(getContext());
        this.sentence = (TextView) layout.findViewById(R.id.example_sentence);
        this.tr_sentence = (TextView) layout.findViewById(R.id.example_tr_sentence);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if(listener!=null)
            listener.start();
        sentence.setText(object.getSentence());
        tr_sentence.setText(object.getTranslatedSentence());
        speaker.speakExample(object);
        speaker.setSpeakerListener(() -> {
            if(listener!=null)
                listener.end();
        });
    }

    public void warp(Data object) {
        this.object = object;
    }

    public void setListener(FragmentListener listener) {
        this.listener = listener;
    }
}
